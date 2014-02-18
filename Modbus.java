
import java.io.IOException;
import java.lang.Integer;
import java.net.Socket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Modbus {
    private static final int MODBUS_PORT = 502;
    private static final byte MODBUS_READ = 0x03;
    private static final byte MODBUS_WRITE = 0x10;

    public Modbus(String ip, byte slave)
	throws UnknownHostException
    {
	this.ip = Inet4Address.getByName(ip);
	this.slave = slave;
	tid = 0;
    }

    public Modbus(byte[] ip, byte slave)
	throws UnknownHostException
    {
	this.ip = Inet4Address.getByAddress(ip);
	this.slave = slave;
	tid = 0;
    }

    public Modbus(InetAddress ip, byte slave)
    {
	this.ip = ip;
	this.slave = slave;
	tid = 0;
    }

    private void printException(byte[] response)
    {
	if(1 <= response[1] && response[1] <= 11) {
	    int i = response[1] - 1;
	    String[] errors = {
		"Illegal Function\n",
		"Illegal Data Value\n",
		"Slave Device Failure\n",
		"Acknowledge\n",
		"Slave Device Busy\n",
		"Memory Parity Error\n",
		"Gateway Path Unavailable\n",
		"Gateway Target Device Failed To Respond\n",
	    };
	    System.err.print(errors[i]);
	}
    }
    
    protected ByteBuffer createReadCmd(short addr, short size)
    {
	/* Tell the drive we need to read registers at the address
	 * Modbus Read Command:
	 * Transaction Identifier: Byte 1-2
	 * Protocol Identifier = 0: Byte 3-4
	 * Length (bytes) field (upper byte) = 0: Byte 5-6
	 * Unit identifier (previously slave address): Byte 7
	 * Function Code: Byte 8
	 * Starting Address: Bytes 9-10
	 * Quantity of Registers: Bytes 11-12
	 */
	final short readlen = 12;
	ByteBuffer cmd = ByteBuffer.allocate(readlen);
	cmd.order(ByteOrder.BIG_ENDIAN);
	cmd.putShort(tid);
	tid++;
	cmd.putShort((short)0);
	final short cmdlen = 6;
	cmd.putShort(cmdlen);
	cmd.put((byte)0);
	cmd.put(MODBUS_READ);
	cmd.putShort(addr);
	cmd.putShort(size);
	return cmd;
    }

    protected short[] readResponse(Socket socket, int expectedlen)
	throws IOException
    {
	/* Modbus Read Response:
	 * Transaction Identifier: Byte 1-2
	 * Protocol Identifier = 0: Byte 3-4
	 * Length (bytes) field (upper byte) = 0: Byte 5-6
	 * Unit identifier (previously slave address): Byte 7
	 * Function Code: Byte 8
	 * Number of Bytes: Byte 9
	 * Data: Byte 10-
	 */
	final int headerlen = 9;
	final int bytes = headerlen + expectedlen;
	/* Just block until we have the data */
	while(socket.getInputStream().available() < bytes);
	/* Throwing away the header is not the most robust thing
	 * to do (I currently have no reason to believe I shouldn't),
	 * but for now it will work
	 */
	socket.getInputStream().skip(headerlen);
	/* Now read the data! */
	short dest[] = new short[expectedlen / 2];
	for(int i = 0; i < dest.length; i++) {
	    byte upper = (byte)socket.getInputStream().read();
	    byte lower = (byte)socket.getInputStream().read();
	    dest[i] = (short)(upper << 8);
	    dest[i] += lower;
	}
	return dest;
    }

    public short[] readRegisters(short addr, short numregs)
	throws IOException, ModbusResponseException
    {
	short size = (short)(2 * numregs);
	Socket socket = new Socket(ip, MODBUS_PORT);
	ByteBuffer cmd = createReadCmd(addr, numregs);
	socket.getOutputStream().write(cmd.array());

	short dest[] = readResponse(socket, size);
	socket.close();
	return dest;
    }

    void writeRegisters(short regs[], short addr)
	throws IOException, Exception
    {
	Socket socket = new Socket(ip, MODBUS_PORT);
	/* Modbus Read Command:
	 * Transaction Identifier: Byte 1-2
	 * Protocol identifier = 0: Byte 3-4
	 * Length (bytes) field (upper byte) = 0: Byte 5-6
	 * Unit identifier (previously slave address): Byte 7
	 * Function Code: Byte 8
	 * Starting Address: Bytes 9-10
	 * Quantity of Registers: Bytes 11-12
	 */
	ByteBuffer cmd = ByteBuffer.allocate(12 + 2 * regs.length);
	cmd.putShort(tid);
	tid++;
	cmd.putShort((short)0);
	short length = (short)(7 + 2 * regs.length);
	cmd.putShort(length);
	cmd.put((byte)0);
	cmd.put(MODBUS_WRITE);
	cmd.putShort(addr);
	cmd.putShort((short)regs.length);
	cmd.put((byte)(regs.length * 2));
	for(int i = 0; i < regs.length; i++) {
	    cmd.putShort(regs[i]);
	}
	socket.getOutputStream().write(cmd.array());
    }

    InetAddress ip;
    byte slave;
    short tid;

    class ModbusResponseException extends Exception
    {
    }
}
