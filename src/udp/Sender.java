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
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 *
 * @author Peter
 */
public class Sender {
    int ACK = -1;
    byte lastSent = -1;
    String inputPath;
    String outputFile;
    
    class OutThread extends Thread{
        private DatagramSocket sk_out;
        private int dstPort;
        FileInputStream fis;
        public OutThread(DatagramSocket sk_out, int dstPort) throws FileNotFoundException{
            this.sk_out = sk_out;
            this.dstPort = dstPort;
            fis = new FileInputStream(new File(inputPath));
            
        }
        public void run(){
            byte[] outData = new byte[1000];
            try{
                InetAddress dstAdd = InetAddress.getByName("127.0.0.1");             
                while(lastSent<ACK+10){
                    //POPULATE BUFFER
                    addSequence(outData);
                    addCRC(outData);     
                    DatagramPacket outPacket = new DatagramPacket(outData, outData.length, dstAdd, dstPort);
                    //InThread th_in = new InThread(); 
                }       
            }
            catch(Exception e){
                
            }
        }
        private void addSequence(byte[] outData){
            if(lastSent==Byte.MAX_VALUE){
                lastSent = -1;
            }
            lastSent++;
            outData[4]=lastSent;
        }
        private void addCRC(byte[] outData){
            CRC32 crc = new CRC32();
            crc.update(outData, 4, outData.length-4);
            int checksum = (int) crc.getValue();
            for(int i=3;i>=0;i--){
                outData[i] = (byte)(checksum%256);
                checksum = checksum>>8;
            }
        }
        
    }
    class InThread extends Thread{
        private DatagramSocket sk_in;
        
        public InThread(DatagramSocket sk4){
            this.sk_in = sk4;
        }
        public void run(){
            byte[] inBytes = new byte[1000];
            DatagramPacket inPacket = new DatagramPacket(inBytes,inBytes.length);
            boolean flag = true;
            try{
                while(flag){
                    sk_in.receive(inPacket);
                    processACK(inPacket.getData());
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
    public Sender(int sk1Port, int sk4Port, String inputPath, String outputFile) throws SocketException{
        this.inputPath = inputPath;
        this.outputFile = outputFile;
        DatagramSocket sk1, sk4;
        sk1 = new DatagramSocket();
        sk4 = new DatagramSocket(sk4Port);

        OutThread th_out = new OutThread(sk1, sk1Port);
        
        th_out.start();
    }
    public static void main(String[] args) throws SocketException{
        if(args.length!=4){
            System.err.println("WRONG ARGUMENT!");
        }
        else{
            new Sender(Integer.parseInt(args[0]),Integer.parseInt(args[1]),args[2],args[3]);
        }
    }
}
