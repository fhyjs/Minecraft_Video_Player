package org.eu.hanana.reimu.mcvp;

import cn.fhyjs.cirno.Callback;
import cn.fhyjs.cirno.Telnet;
import org.eu.hanana.reimu.tnmc.Base;
import org.eu.hanana.reimu.tnmc.ExTelnet;

import java.io.IOException;

public class Main implements Runnable{
    public static ExTelnet baseTelnet;
    public static void main(String[] args) {
        System.out.println("start!");
        org.eu.hanana.reimu.tnmc.Base.start(args,new Main());
    }

    @Override
    public void run() {
        new Thread(()-> {
            try {
                baseTelnet = new ExTelnet(Base.host);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }).start();
        while (baseTelnet==null||!baseTelnet.socket.isConnected()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            Base.regFunc("playvid",new FuncPlayvid());
            baseTelnet.setCallback(new Callback() {
                @Override
                public void OnReceive(String s) {
                    System.out.println(s);
                }

                @Override
                public void OnExit() {

                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        while(baseTelnet.socket.isConnected()) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException var3) {
                throw new RuntimeException(var3);
            }
        }
    }
}