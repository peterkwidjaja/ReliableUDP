/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package udp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 *
 * @author Peter
 */
public class Sender {
    int ACK = -1;
    int lastSent = -1;
    String inputPath;
    String outputFile;
    boolean resend = false;
    int packResend = -1;
    
    class OutThread extends Thread{
        private final int headerSize = 6; //define size of header in the packet
        private DatagramSocket sk_out;
        private int dstPort;
        FileInputStream fis;
        DatagramPacket[] packets;
        public OutThread(DatagramSocket sk_out, int dstPort) throws FileNotFoundException, IOException{
            this.sk_out = sk_out;
            this.dstPort = dstPort;
            fis = new FileInputStream(new File(inputPath));
            int packetNo = (int) Math.ceil(fis.available()/993.0);
            packets = new DatagramPacket[packetNo+1]; //1 packet is dedicated to send filename
            prepare();
        }
        private void prepare() throws IOException{
            byte[] fileBuff = new byte[994];
            byte[] outData = new byte[1000];
            InetAddress dstAdd = InetAddress.getByName("127.0.0.1"); 
            
            //Prepare first packet which contains filename
            byte[] filename = outputFile.getBytes();
            byte[] outFilename = new byte[filename.length+headerSize];
            outFilename[5] = 1; //set FIRST
            outFilename[4] = 0; //set sequence to 0
            prepareCRC(outFilename, outFilename.length-headerSize);
            packets[0] = new DatagramPacket(outFilename,outFilename.length,dstAdd,dstPort);
            
            //preparing each packet to be sent
            for(int i=1; i<packets.length; i++){
                byte EOF = 0;
                
                if(i==packets.length-1){
                    EOF = 2;
                    fileBuff = new byte[fis.available()];
                    outData = new byte[fileBuff.length+headerSize];
                }
                outData[5] = EOF;
                fis.read(fileBuff);
                byte seq = (byte)(i%128); //convert sequence to byte, resetting when necessary
                outData[4] = seq;
                System.arraycopy(fileBuff,0,outData,headerSize,fileBuff.length);
                prepareCRC(outData, outData.length-headerSize);   
                packets[i] = new DatagramPacket(outData,outData.length,dstAdd,dstPort);
            }
        }
        private void prepareCRC(byte[] outData, int length){ //Add CRC bits to the first 4 bytes of the packet
            CRC32 crc = new CRC32();
            crc.update(outData, headerSize, length);
            int check = (int) crc.getValue();
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt(check);
            bb.flip();
            for(int i=0; i<4; i++){
                outData[i] = bb.get();
            }
        }
        public void run(){
            try{
                boolean flag = true;
                while(flag){
                    if(resend){
                        resend = false;
                        for(int i=packResend; i<=lastSent; i++){
                            sk_out.send(packets[packResend]);
                        }
                    }
                    while(lastSent<ACK+9){
                        lastSent++;
                        sk_out.send(packets[lastSent]);
                    }
                }               
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    class InThread extends Thread{
        private DatagramSocket sk_in;
        public InThread(DatagramSocket sk4){
            this.sk_in = sk4;
        }
        public void run(){
            byte[] inBytes = new byte[5];
            DatagramPacket inPacket = new DatagramPacket(inBytes,inBytes.length);
            boolean flag = true;
            try{
                while(flag){
                    if(lastSent>ACK){
                        sk_in.setSoTimeout(100);
                        try{
                            sk_in.receive(inPacket);
                            processACK(inPacket.getData());
                        }
                        catch(SocketTimeoutException e){
                            resend = true;
                            packResend = ACK+1;
                        }
                    } 
                }                
            }
            catch(IOException ioe){
                
            }
        }
        private void processACK(byte[] inBytes){
            ByteBuffer bb = ByteBuffer.wrap(inBytes);
            int check = bb.getInt();
            CRC32 crc = new CRC32();
            crc.update(inBytes, 4, 1);
            if(check==crc.getValue()){
                byte ack = inBytes[4];
                if(ACK<ack){
                    ACK = ack;
                }
            }
        }
    }
    public Sender(int sk1Port, int sk4Port, String inputPath, String outputFile) throws SocketException, IOException{
        this.inputPath = inputPath;
        this.outputFile = outputFile;
        DatagramSocket sk1, sk4;
        sk1 = new DatagramSocket();
        sk4 = new DatagramSocket(sk4Port);

        OutThread th_out = new OutThread(sk1, sk1Port);
        InThread th_in = new InThread(sk4);
        th_out.start();
    }
    public static void main(String[] args) throws SocketException, IOException{
        if(args.length!=4){
            System.err.println("WRONG ARGUMENT!");
        }
        else{
            new Sender(Integer.parseInt(args[0]),Integer.parseInt(args[1]),args[2],args[3]);
        }
    }
}
