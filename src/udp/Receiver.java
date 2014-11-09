/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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
        
        DatagramPacket inPacket = new DatagramPacket(inData, inData.length);
        boolean flag = true;
        while(flag){
            sk2.receive(inPacket);
            System.out.println("Receive packet!");
            if(!checksum(inPacket.getData(),inPacket.getLength())){
                System.out.println("CRC error");
            }
            else if(!checkOrder(inPacket.getData())){
                //sendACK(sk3Port);
                System.out.println("Sending wrong order ACK"+ACK);
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
                System.out.println("Sending ACK "+ ACK);
                flag = processPacket(inPacket.getData(),inPacket.getLength()); //flag will be false for the last packet
            }
        }
        outFile.close();
        boolean end = true;
        while(end){
            sk2.setSoTimeout(500);
            try{
                sk2.receive(inPacket);
                sendACK(sk3Port);
            }
            catch(SocketTimeoutException e){
                end = false;
            }
        }
        sk2.close();
    }
    private void initStream(String filename) throws IOException{
        File newFile = new File(path,filename);
        if(!newFile.exists()){
            newFile.createNewFile();
        }
        outFile = new BufferedOutputStream(new FileOutputStream(newFile));
    }
    
    //return false if it is the last packet
    private boolean processPacket(byte[] inBytes, int length) throws IOException{
        byte EOF = inBytes[5];
        boolean last = true;
        if(EOF==1){
            byte[] fileBytes = new byte[length-6];
            System.arraycopy(inBytes, 6, fileBytes, 0, length-6);
            String filename = new String(fileBytes);
            System.out.println("new file: "+filename+ " "+fileBytes);
            initStream(filename);
        }
        else{
            if(outFile!=null){
                outFile.write(inBytes, 6, length-6);
            }
            else{
                System.err.println("filename has not been initialized");
            }
        }
        
        if(EOF==2){
            last = false;
        }
        return last;
    }
    private boolean checksum(byte[] inBytes, int length){
        CRC32 crc = new CRC32();
        ByteBuffer bb = ByteBuffer.wrap(inBytes);
        int check = bb.getInt();
        bb.clear();
        System.out.println("CRC for "+length+" from server: "+check);
        crc.update(inBytes, 4, length-4);     
        int temp = (int)crc.getValue();
        return check == temp;
    }
    private boolean checkOrder(byte[] inBytes){
        byte seq = inBytes[4];
        System.out.println("Receive sequence: "+seq);
        return seq == (ACK+1)%128;
    }
    private void sendACK(int sk3port) throws UnknownHostException, IOException{
        CRC32 crc = new CRC32();
        byte[] out = new byte[5];
        InetAddress outAdd = InetAddress.getByName("127.0.0.1");
        out[4] = ACK;
        crc.update(out, 4, 1);
        ByteBuffer bb = ByteBuffer.allocate(4);
        int check = (int)crc.getValue();
        bb.putInt(check);
        bb.flip();
        for(int i=0; i<4; i++){
            out[i] = bb.get();
        }
        DatagramPacket outPacket = new DatagramPacket(out,out.length,outAdd,sk3port);
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
