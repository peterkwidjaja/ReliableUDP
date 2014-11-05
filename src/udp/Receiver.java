/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package udp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.zip.CRC32;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *
 * @author Peter
 */
public class Receiver {
    final int MAX_WINDOW = 10;
    BufferedOutputStream outFile;
    DatagramSocket sk2, sk3;
    String path;
    byte ACK = -1;
    public Receiver(int sk2Port, int sk3Port, String path) throws IOException{
        //Initialize the two socket
        sk2 = new DatagramSocket(sk2Port);  //sk2 is to receive from UnreliNet
        sk3 = new DatagramSocket();         //sk3 is to send to UnreliNet from Receiver
        this.path = path;
        byte[] inData = new byte[1000]; //Define max packet size to 1000
        File tempFile = new File(path, "temp");
        if(!tempFile.exists())
            tempFile.createNewFile();
        BufferedOutputStream tempStream = new BufferedOutputStream(new FileOutputStream(tempFile));
        
        DatagramPacket inPacket = new DatagramPacket(inData, inData.length);
        boolean flag = true;
        while(flag){
            sk2.receive(inPacket);
            if(!checksum(inData)){
                //drop packet
            }
            else if(!checkOrder(inData)){
                sendACK(sk3Port);
            }
            else{
                //Advance ACK
                if(ACK==Byte.MAX_VALUE){
                    ACK=0;
                }
                else{
                    ACK++;
                }
                sendACK(sk3Port);
                flag = processPacket(inData);
            }
        }
        outFile.close();
    }
    private void initStream(String filename) throws IOException{
        File newFile = new File(path,filename);
        if(!newFile.exists()){
            newFile.createNewFile();
        }
        outFile = new BufferedOutputStream(new FileOutputStream(newFile));
    }
    
    //return false if it is the last packet
    private boolean processPacket(byte[] inBytes) throws IOException{
        ByteBuffer bb = ByteBuffer.wrap(inBytes);
        int temp = (int) bb.getShort(5);
        int len = temp/4;
        boolean last = true;
        if(temp%2==1){
            String filename = new String(Arrays.copyOfRange(inBytes, 7, len+6));
            initStream(filename);
        }
        else{
            if(outFile!=null){
                outFile.write(inBytes, 7, len);
            }
            else{
                //ERROR!
            }
        }
        if((temp/2)%2==1){
            last = false;
        }
        return last;
    }
    private boolean checksum(byte[] inBytes){
        CRC32 crc = new CRC32();
        ByteBuffer bb = ByteBuffer.wrap(inBytes);
        int check = bb.getInt();
        crc.update(inBytes, 4, inBytes.length-4);
        long temp = crc.getValue();
        return check == temp;
    }
    private boolean checkOrder(byte[] inBytes){
        byte seq = inBytes[4];
        return seq == ACK;
    }
    private void sendACK(int sk3port) throws UnknownHostException, IOException{
        byte[] out = new byte[1];
        InetAddress outAdd = InetAddress.getByName("127.0.0.1");
        out[0] = ACK;
        DatagramPacket outPacket = new DatagramPacket(out,1,outAdd,sk3port);
        sk3.send(outPacket);
    }
    public static void main(String[] args) throws IOException{
        
        if(args.length!=3){
            System.err.println("Wrong argument");
        }
        else{
            new Receiver(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2]);
        }
    }
}
