
import java.io.IOException;
import java.net.Socket;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Modbus {
    private static final int MODBUS_PORT = 502;
    private static final byte MODBUS_READ = 0x03;

    public Modbus(String ip)
	throws UnknownHostException
    {
	this.ip = Inet4Address.getByName(ip);
	tid = 0;
    }

    public Modbus(InetAddress ip)
    {
	this.ip = ip;
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

    public void readRegisters(short[] dest, short addr, short size)
	throws IOException, Exception
    {
	/* Registers are either 2 or 4 bytes in size */
	assert size % 2 == 0;
	size /= 2;
	Socket socket = new Socket(ip, MODBUS_PORT);
	/* Tell the drive we need to read registers at the address
	 * Modbus Read Command:
	 * Transaction Identifier: Byte 1-2
	 * Protocol identifier = 0: Byte 3-4
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
	cmd.putShort(readlen);
	cmd.put((byte)0);
	cmd.put(MODBUS_READ);
	cmd.putShort(addr);
	cmd.putShort(size);
	socket.getOutputStream().write(cmd.array());
	/* Get the values of the registers.
	 * First wait for it to have arrived
	 * Then read the packet.
	 * Response Format:
	 * Function Code: Byte 0
	 * Byte Count: Byte 1
	 * Register Values: Bytes 2-(2*size+2)
	 */
	while(socket.getInputStream().available() < 2 + 2 * size &&
	      socket.isConnected());
	byte[] check = new byte[2];
	socket.getInputStream().read(check);
	/* Got the header, check for errors */
	assert(check[0] == MODBUS_READ);
	if(socket.getInputStream().available() < 2 + 2 * size) {
	    printException(check);
	    throw new ModbusReturnException();
	}
	/* We're clear. Now just get the data,
	 * and put it in a usable format (easier said than done) */
	byte[] temp = new byte[2 * size];
	socket.getInputStream().read(temp);
	ByteBuffer buffer = ByteBuffer.allocate(2 * size);
	buffer.order(ByteOrder.BIG_ENDIAN);
	buffer.put(temp);
	for(int i = 0; i < size; i++)
	    dest[i] = buffer.getShort();
	socket.close();
    }

    void writeRegisters(byte[] dest, short addr, short size)
	throws IOException, Exception
    {
	/* Registers are either 2 or 4 bytes in size */
	assert size % 2 == 0;
	size /= 2;
	Socket socket = new Socket(ip, MODBUS_PORT);
	/* Modbus Read Command:
	 * Function Code: Byte 0
	 * Starting Address: Bytes 1-2
	 * Quantity of Registers: Bytes 3-4
	 */
	ByteBuffer cmd = ByteBuffer.allocate(5);
	cmd.put(MODBUS_READ);
	cmd.putShort(addr);
	cmd.putShort(size);
	socket.getOutputStream().write(cmd.array());
    }

    InetAddress ip;
    short tid;

    class ModbusReturnException extends Exception
    {
    }
}
