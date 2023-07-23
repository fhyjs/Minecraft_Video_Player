package org.eu.hanana.reimu.mcvp;

import com.diamondpants.spritecraft.Blueprint;
import com.diamondpants.spritecraft.Generator;
import com.diamondpants.spritecraft.MaterialSet;
import com.diamondpants.spritecraft.frontend.MainFrame;
import com.diamondpants.spritecraft.ticators.Imageticator;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.eu.hanana.reimu.tnmc.Base;
import org.eu.hanana.reimu.tnmc.ExTelnet;
import org.eu.hanana.reimu.tnmc.FuncBase;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.eu.hanana.reimu.mcvp.Glutil.mergeImages;
import static org.eu.hanana.reimu.mcvp.Glutil.splitImage;

public class FuncPlayvid implements FuncBase,Runnable {
    Generator generator;
    List<BlockPos> blocks = new ArrayList<>();
    Map<Integer,Map<BlockPos,Block>> blockToSet = new HashMap<>();
    Map<Integer,Map<BlockPos,Block>> oldBlockToSet = new HashMap<>();
    Map<Integer,ExTelnet> conns = new HashMap<>();
    public boolean nextframe;
    FFmpegFrameGrabber frameGrabber;
    private boolean shouldProceed = false; // 是否应该继续处理下一帧
    @Deprecated(since = "Deprecated")
    @Override
    public void run() {
        //CanvasFrame canvasFrame = new CanvasFrame("sss");
        //canvasFrame.setVisible(true);
        // 创建视频帧抓取器
        frameGrabber = new FFmpegFrameGrabber("C:\\Users\\a\\Downloads\\Video\\av12.mp4");
        int threadNum = blocks.size()/500;
        try {
            for (int i = 0; i < threadNum; i++) {
                conns.put(i,new ExTelnet(Base.host));
            }
            frameGrabber.start();
            Frame frame;
            VidTicker ticker = new VidTicker();
            ticker.start();
            while ((frame = frameGrabber.grabImage()) != null) {
                try {
                    frame = Glutil.flipFrame(frame,true,false);
                    // 分割图像
                    List<BufferedImage> subImages = splitImage(new Java2DFrameConverter().convert(frame), threadNum);
                    // 多线程
                    procImages(subImages, threadNum);
                    synchronized (this) {
                        while (!shouldProceed) {
                            wait(); // 等待 shouldProceed 为 true 时唤醒
                        }
                        shouldProceed = false; // 处理完一帧后置为 false，等待下一次唤醒
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            ticker.stop();
            frameGrabber.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private class VidTicker extends Thread{
        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (FuncPlayvid.this) {
                        shouldProceed = true;
                        FuncPlayvid.this.notify(); // 唤醒处理线程
                    }
                    Thread.sleep((long) (1000 / frameGrabber.getVideoFrameRate()));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    private void convertImage(BufferedImage inputImage,int thn) {
        if (!blockToSet.containsKey(thn)) blockToSet.put(thn,new HashMap<>());
        if (!oldBlockToSet.containsKey(thn)) oldBlockToSet.put(thn,new HashMap<>());
        Map<BlockPos, Block> tbos = blockToSet.get(thn);
        Map<BlockPos, Block> tobos = oldBlockToSet.get(thn);
        try {
            Blueprint blueprint = generator.run(inputImage);
            MaterialSet materialSet = blueprint.getMaterialSet();
            byte[][] usedMaterials = blueprint.getUsedMaterials();

            for(int i = usedMaterials.length - 1; i >= 0; --i) {
                for(int j = usedMaterials[i].length - 1; j >= 0; --j) {
                    byte materialNum = usedMaterials[i][j];
                    if (materialNum != -128) {
                        if (materialNum==18)
                            materialNum=2;
                        tbos.put(new BlockPos(x0+i,y0,z0+j+(usedMaterials[i].length)*thn),new Block(materialSet.getMaterial(materialNum).getBlockID(),materialSet.getMaterial(materialNum).getBlockData()));
                    }
                }
            }
            boolean c = false;
            for (BlockPos bp : tbos.keySet()) {
                if (tobos.containsKey(bp)&&tbos.get(bp).equals(tobos.get(bp))) {
                    continue;
                }
                tobos.put(bp,tbos.get(bp));
                conns.get(thn).send(String.format("/id_setblock %d %d %d %d %d",bp.x,bp.y,bp.z,tbos.get(bp).id,tbos.get(bp).state));
                c=true;
            }
            if (c) {
                oldBlockToSet.put(thn,new HashMap<>(tbos));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void procImages(List<BufferedImage> subImages, int numThreads) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (BufferedImage subImage : subImages) {
            executor.submit(() -> convertImage(subImage,subImages.indexOf(subImage)));
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS);

    }
    int x1,x2,y1,y2,z1,z2,x0,y0,z0;
    @Override
    public void run(String[] args) {
         x1=Integer.parseInt(args[1]);
         y1=Integer.parseInt(args[2]);
         z1=Integer.parseInt(args[3]);
         x2=Integer.parseInt(args[4]);
         y2=Integer.parseInt(args[5]);
         z2=Integer.parseInt(args[6]);

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
            generator.setMaxHeight(Math.abs(z2-z1)+1);
            generator.setMaxWidth(Math.abs(y2-y1)+1);

            x0=x1;
            y0=Math.min(y1,y2);
            z0=Math.min(z1,z2);
        }
        if (y1==y2){
            for (int i = Math.min(x1,x2); i <= Math.max(x1,x2); i++) {
                for (int j = Math.min(z1,z2); j <= Math.max(z1,z2); j++) {
                    blocks.add(new BlockPos(i,y1,j));
                }
            }
            generator.setMaxHeight(Math.abs(x2-x1)+1);
            generator.setMaxWidth(Math.abs(z2-z1)+1);

            y0=y1;
            x0=Math.min(x1,x2);
            z0=Math.min(z1,z2);
        }
        if (z1==z2){
            for (int i = Math.min(x1,x2); i <= Math.max(x1,x2); i++) {
                for (int j = Math.min(y1,y2); j <= Math.max(y1,y2); j++) {
                    blocks.add(new BlockPos(i,j,z1));
                }
            }
            generator.setMaxHeight(Math.abs(x2-x1)+1);
            generator.setMaxWidth(Math.abs(y2-y1)+1);

            z0=z1;
            y0=Math.min(y1,y2);
            x0=Math.min(x1,x2);
        }
        for (BlockPos block : blocks) {
            try {
                Main.baseTelnet.send(String.format("/setblock %d %d %d minecraft:%s", block.x,block.y,block.z,args[7]));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        generator.getMaterialSet().setAll();
        generator.setSideView(false);
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BlockPos blockPos = (BlockPos) o;

            if (x != blockPos.x) return false;
            if (y != blockPos.y) return false;
            return z == blockPos.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
    public static class Block{
        public int id,state;
        public Block(int id,int state){
            this.id=id;
            this.state=state;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Block block = (Block) o;

            if (id != block.id) return false;
            return state == block.state;
        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + state;
            return result;
        }
    }
}
