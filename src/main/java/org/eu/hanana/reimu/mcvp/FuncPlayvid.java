package org.eu.hanana.reimu.mcvp;

import com.diamondpants.spritecraft.Blueprint;
import com.diamondpants.spritecraft.Generator;
import com.diamondpants.spritecraft.MaterialSet;
import com.diamondpants.spritecraft.frontend.MainFrame;
import com.diamondpants.spritecraft.ticators.Imageticator;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.eu.hanana.reimu.tnmc.ExTelnet;
import org.eu.hanana.reimu.tnmc.FuncBase;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class FuncPlayvid implements FuncBase,Runnable {
    Generator generator;
    List<ExTelnet> telnetList = new ArrayList<>();
    List<BlockPos> blocks = new ArrayList<>();
    List<Block> blockToSet = new ArrayList<>();
    @Override
    public void run() {
        CanvasFrame canvasFrame = new CanvasFrame("sss");
        canvasFrame.setVisible(true);
        // 创建视频帧抓取器
        FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber("C:\\Users\\a\\Downloads\\av12.mp4");
        try {
            frameGrabber.start();
            Frame frame;
            while ((frame = frameGrabber.grabImage()) != null) {
                frame = scaleFrame(frame,1,1);
                Blueprint blueprint = generator.run(new Java2DFrameConverter().convert(frame));
                canvasFrame.showImage(new Java2DFrameConverter().getFrame(Imageticator.imageticate(blueprint, MainFrame.TESTIMAGEMODE)));

                MaterialSet materialSet = blueprint.getMaterialSet();
                byte[][] usedMaterials = blueprint.getUsedMaterials();

                for(int i = usedMaterials.length - 1; i >= 0; --i) {
                    for(int j = usedMaterials[i].length - 1; j >= 0; --j) {
                        byte materialNum = usedMaterials[i][j];
                        if (materialNum != -128) {
                            materialSet.getMaterial(materialNum).getName();
                        }
                    }
                }
                Thread.sleep((long) (1000/frameGrabber.getFrameRate()));
            }
            frameGrabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void run(String[] args) {
        int x1=Integer.parseInt(args[1]);
        int y1=Integer.parseInt(args[2]);
        int z1=Integer.parseInt(args[3]);
        int x2=Integer.parseInt(args[4]);
        int y2=Integer.parseInt(args[5]);
        int z2=Integer.parseInt(args[6]);

        blocks.clear();
        try {
            generator = new Generator();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (x1==x2){
            for (int i = Math.min(y1,y2); i <= Math.max(y1,y2); i++) {
                for (int j = Math.min(z1,z2); j <= Math.max(z1,z2); j++) {
                    blocks.add(new BlockPos(x1,i,j));
                }
            }
            generator.setMaxWidth(Math.abs(z2-z1)+1);
            generator.setMaxHeight(Math.abs(y2-y1)+1);
        }
        if (y1==y2){
            for (int i = Math.min(x1,x2); i <= Math.max(x1,x2); i++) {
                for (int j = Math.min(z1,z2); j <= Math.max(z1,z2); j++) {
                    blocks.add(new BlockPos(i,y1,j));
                }
            }
            generator.setMaxWidth(Math.abs(x2-x1)+1);
            generator.setMaxHeight(Math.abs(z2-z1)+1);
        }
        if (z1==z2){
            for (int i = Math.min(x1,x2); i <= Math.max(x1,x2); i++) {
                for (int j = Math.min(y1,y2); j <= Math.max(y1,y2); j++) {
                    blocks.add(new BlockPos(i,j,z1));
                }
            }
            generator.setMaxWidth(Math.abs(x2-x1)+1);
            generator.setMaxHeight(Math.abs(y2-y1)+1);
        }
        for (BlockPos block : blocks) {
            try {
                Main.baseTelnet.send(String.format("/setblock %d %d %d minecraft:%s", block.x,block.y,block.z,args[7]));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        generator.getMaterialSet().setAll();
        new Thread(this).start();
    }
    private static Frame scaleFrame(Frame frame, double sw,double sh) {
        Java2DFrameConverter converter = new Java2DFrameConverter();
        BufferedImage bufferedImage = converter.getBufferedImage(frame);

        int scaledWidth = (int) (bufferedImage.getWidth() * sw);
        int scaledHeight = (int) (bufferedImage.getHeight() * sh);

        Image scaledImage = bufferedImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        BufferedImage scaledBufferedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2d = scaledBufferedImage.createGraphics();
        g2d.drawImage(scaledImage, 0, 0, null);
        g2d.dispose();

        return converter.getFrame(scaledBufferedImage);
    }
    public static class BlockPos{
        public int x,y,z;
        public BlockPos(int x,int y,int z){
            this.x=x;
            this.y=y;
            this.z=z;
        }
    }
    public static class Block{
        public String name;
        public BlockPos pos;
        public Block(String name,BlockPos bp){
            this.name=name;
            this.pos=bp;
        }
    }
}
