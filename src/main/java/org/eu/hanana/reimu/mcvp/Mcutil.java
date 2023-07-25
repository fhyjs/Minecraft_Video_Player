package org.eu.hanana.reimu.mcvp;

import cn.fhyjs.cirno.Callback;
import org.eu.hanana.reimu.tnmc.Base;
import org.eu.hanana.reimu.tnmc.ExTelnet;

import java.io.IOException;

public class Mcutil {
    public static Side getSide() {
        final Side[] side = new Side[1];
        try {
            ExTelnet telnet = new ExTelnet(Base.host);
            telnet.setCallback(new Callback() {
                @Override
                public void OnReceive(String s) {
                    System.out.println(s);
                    if (s.equals("hello from The Project Jntm mod!")) return;
                    side[0] = Side.valueOf(s);
                    try {
                        telnet.send("exit");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void OnExit() {

                }
            });
            telnet.send("/side");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int to = 0;
        while (side[0]==null){
            try {
                if (to>100) return null;
                Thread.sleep(10);
                to++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return side[0];
    }
    public static void say(String nr){
        try {
            Main.baseTelnet.send("/say " + nr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public  enum Side{
        CLIENT,
        SERVER,
        BUCKET
    }
}
