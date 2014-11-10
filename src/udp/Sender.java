/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

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
    int lastSeq = -1;
    String inputPath;
    String outputFile;
    boolean resend = false;
    int packResend = -1;
    boolean lastReceived = false;
    boolean lastPacket = false;
    int lastACK = -1;
    boolean start = true;
    class OutThread extends Thread{
        private final int headerSize = 7; //define size of header in the packet
        private DatagramSocket sk_out;
        private int dstPort;
        FileInputStream fis;
        DatagramPacket[] packets;
        public OutThread(DatagramSocket sk_out, int dstPort) throws FileNotFoundException, IOException{
            this.sk_out = sk_out;
            this.dstPort = dstPort;
            fis = new FileInputStream(new File(inputPath));
            int packetNo = (int) Math.ceil(fis.available()/993.0);
            //System.out.println("to send "+packetNo);
            packets = new DatagramPacket[packetNo+1]; //1 packet is dedicated to send filename
            prepare();
        }
        private void prepare() throws IOException{
            byte[] fileBuff = new byte[993];     
            InetAddress dstAdd = InetAddress.getByName("127.0.0.1"); 
            
            //Prepare first packet which contains filename
            byte[] filename = outputFile.getBytes();
            byte[] outFilename = new byte[filename.length+headerSize];
            outFilename[6] = 1; //set FIRST
            outFilename[4] = outFilename[5] = 0; //set sequence to 0
            System.arraycopy(filename, 0, outFilename, headerSize, filename.length);
            prepareCRC(outFilename, outFilename.length-4);
            packets[0] = new DatagramPacket(outFilename,outFilename.length,dstAdd,dstPort);
            
            //preparing each packet to be sent
            ByteBuffer bb = ByteBuffer.allocate(2);
            for(int i=1; i<packets.length; i++){
                byte[] outData;
                byte EOF = 0;
                if(i==packets.length-1){
                    EOF = 2;
                    fileBuff = new byte[fis.available()];
                    outData = new byte[fileBuff.length+headerSize];
                }
                else{
                    outData = new byte[1000];
                }
                outData[6] = EOF;
                fis.read(fileBuff);
                short seq = (short)(i%32768);
                bb.putShort(seq);
                bb.flip();
                outData[4]=bb.get();
                outData[5]=bb.get();
                bb.clear();
                System.arraycopy(fileBuff,0,outData,headerSize,fileBuff.length);
                prepareCRC(outData, outData.length-4);
                packets[i] = new DatagramPacket(outData,outData.length,dstAdd,dstPort);
            }
            
        }
        private void prepareCRC(byte[] outData, int length){ //Add CRC bits to the first 4 bytes of the packet
            CRC32 crc = new CRC32();
            crc.update(outData, 4, length);
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
                    while(resend){
                        resend = false;
                        for(int i=packResend; i<=lastSent; i++){
                            if(resend)
                                break;
                            if(i<=ACK){
                                lastSent = ACK;
                                break;
                            }
                            sk_out.send(packets[i]);
                            
                            //System.out.println("send repeat " + i);
                            start = true;
                        }
                    }
                    while(lastSent<ACK+10 && lastSent<packets.length-1){
                        if(resend) break; //if something needs to be resent break, attend to the resend first!
                        
                        lastSent++;
                        lastSeq = lastSent%32768;
                        sk_out.send(packets[lastSent]);
                        if(lastSent == ACK+1)
                            start = true;
                        //System.out.println("sending.." + lastSent+ " "+ACK);
                        if(lastSent==packets.length-1){ //Indicate that the last packet is already sent
                            lastACK = lastSeq;
                            lastPacket = true;
                        }
                    }
                    if(lastReceived){
                        sk_out.close();
                        flag = false;
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
        private int countRep = 0;
        private short repAck = 0;
        private int countLimit = 3;
        public InThread(DatagramSocket sk4){
            this.sk_in = sk4;
        }
        public void run(){
            byte[] inBytes = new byte[6];
            DatagramPacket inPacket = new DatagramPacket(inBytes,inBytes.length);
            boolean flag = true;
            try{
                while(flag){
                    if(lastSent!=ACK){
                        while(!start) {}
                        //System.out.println("Waiting for ACK..");
                        sk_in.setSoTimeout(300);
                        try{
                            sk_in.receive(inPacket);
                            //System.out.println("ACK received!");
                            processACK(inPacket.getData());          
                        }
                        catch(SocketTimeoutException e){
                            //System.out.println("Timeout");
                            packResend = ACK+1;
                            resend = true;
                            //start = false;
                        }
                    }
                    if(lastReceived){   //last ACK received, close thread
                        flag = false;
                    }
                }                
            }
            catch(IOException ioe){
                System.err.println(ioe);
            }
        }
        private void processACK(byte[] inBytes) throws SocketTimeoutException{
            ByteBuffer bb = ByteBuffer.wrap(inBytes);
            //System.out.println("ACK size: "+bb.capacity());
            int check = bb.getInt();
            CRC32 crc = new CRC32();
            crc.update(inBytes, 4, 2);
            if(check==(int)crc.getValue()){
                short ack = bb.getShort();
                //System.out.println("Receive ACK: "+ack);
                
                if(ack==ACK%32768){
                    if(repAck==ack){    
                        countRep++;
                        if(countRep==countLimit){
                            countRep = 0;
                            countLimit = 10;
                            throw new SocketTimeoutException();
                        }
                    }    
                    else{
                        repAck = ack;
                        countRep = 2;
                        countLimit = 3;
                    }
                }
                //Advance ACK only if the received ack is greater than current ACK
                if((ACK%32768)<ack && (lastSeq>=ack)){
                    ACK += ack-(ACK%32768);
                }
                else if((ACK%32768)>lastSeq){
                    if(ack>(ACK%32768)){
                        ACK += ack-(ACK%32768);
                    }
                    else if(ack<=lastSeq){
                        ACK = lastSent - (lastSeq-ack);
                    }
                }
                //Detect last ACK
                if(lastPacket && ACK==lastSent){
                    lastReceived = true;
                }
                //System.out.println("Set ACK to: " + ACK);
            }
            else{
                //System.out.println("ACK corrupted");
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
        th_in.start();
    }
    public static void main(String[] args) throws SocketException, IOException{
        if(args.length!=4){
            System.err.println("Argument: <sk1 port> <sk4 port> <input file> <filename at receiver>");
        }
        else{
            new Sender(Integer.parseInt(args[0]),Integer.parseInt(args[1]),args[2],args[3]);
        }
    }
}
