package org.eu.hanana.reimu.mcvp;

import com.diamondpants.spritecraft.Blueprint;
import com.diamondpants.spritecraft.Generator;
import com.diamondpants.spritecraft.MaterialSet;
import com.diamondpants.spritecraft.frontend.MainFrame;
import com.diamondpants.spritecraft.ticators.Imageticator;
import javazoom.jl.player.Player;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.eu.hanana.reimu.tnmc.Base;
import org.eu.hanana.reimu.tnmc.ExTelnet;
import org.eu.hanana.reimu.tnmc.FuncBase;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaListPlayerComponent;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.embedded.videosurface.ComponentVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.WindowsVideoSurfaceAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.eu.hanana.reimu.mcvp.Glutil.*;
import static org.eu.hanana.reimu.mcvp.Mcutil.say;
import static org.eu.hanana.reimu.tnmc.Base.regFunc;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.ALC11.ALC_ALL_DEVICES_SPECIFIER;
import static org.lwjgl.openal.EXTThreadLocalContext.alcSetThreadContext;
import static org.lwjgl.system.MemoryUtil.NULL;

public class FuncPlayvid implements FuncBase,Runnable {
    Generator generator;
    List<BlockPos> blocks = new ArrayList<>();
    Map<Integer,Map<BlockPos,Block>> blockToSet = new HashMap<>();
    Map<Integer,Map<BlockPos,Block>> oldBlockToSet = new HashMap<>();
    Map<Integer,ExTelnet> conns = new HashMap<>();
    public boolean nextframe;
    FFmpegFrameGrabber frameGrabber;
    private AudioFormat af = null;
    private SourceDataLine sourceDataLine;
    private DataLine.Info dataLineInfo;
    private boolean shouldProceed = false; // 是否应该继续处理下一帧
    boolean playing ;

    @Deprecated(since = "Deprecated")
    @Override
    public void run() {
        //CanvasFrame canvasFrame = new CanvasFrame("sss");
        //canvasFrame.setVisible(true);

        // 创建视频帧抓取器
        frameGrabber = new FFmpegFrameGrabber(args[8]);
        boolean audio = Mcutil.getSide().equals(Mcutil.Side.CLIENT)&&Boolean.parseBoolean(args[9]);
        int threadNum = blocks.size()/500;
        try {
            for (int i = 0; i < threadNum; i++) {
                conns.put(i,new ExTelnet(Base.host));
            }
            say("thread was created");
            frameGrabber.start();
            VidTicker ticker = new VidTicker();
            Frame frame;
            say("ok");
            if (audio){
                new Thread(this::Caudio).start();
                synchronized (this) {
                    wait();
                }
            }
            ticker.start();
            while (( frame = frameGrabber.grabImage()) != null) {
                try {
                    if (frame.type == Frame.Type.VIDEO) {
                        frame = Glutil.flipFrame(frame, true, false);
                        // 分割图像
                        List<BufferedImage> subImages = splitImage(new Java2DFrameConverter().convert(frame), threadNum);
                        // 多线程
                        procImages(subImages, threadNum);
                    }

                    synchronized (this) {

                        if (frame.type == Frame.Type.VIDEO) {
                            while (!shouldProceed) {
                                wait(); // 等待 shouldProceed 为 true 时唤醒
                            }
                            shouldProceed = false; // 处理完一帧后置为 false，等待下一次唤醒
                        }
                    }
 

                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
            ticker.stop();
            frameGrabber.stop();
            frameGrabber.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Deprecated(since = "Deprecated")
    public void Caudio() {
        try {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(args[8]);
            int sampleRate = frameGrabber.getSampleRate();
            int bufferId;
                // 初始化 OpenAL
                long device = alcOpenDevice((CharSequence) null);
                if (device == NULL) {
                    throw new IllegalStateException("Failed to open an OpenAL device.");
                }

                ALCCapabilities deviceCaps = ALC.createCapabilities(device);

                if (!deviceCaps.OpenALC10) {
                    throw new IllegalStateException();
                }
                System.out.println("OpenALC10  : " + deviceCaps.OpenALC10);
                System.out.println("OpenALC11  : " + deviceCaps.OpenALC11);
                System.out.println("ALC_EXT_EFX: " + deviceCaps.ALC_EXT_EFX);

                if (deviceCaps.OpenALC11) {
                    List<String> devices = ALUtil.getStringList(NULL, ALC_ALL_DEVICES_SPECIFIER);
                    if (devices == null) {
                        checkALCError(NULL);
                    } else {
                        for (int i = 0; i < devices.size(); i++) {
                            System.out.println(i + ": " + devices.get(i));
                        }
                    }
                }

                String defaultDeviceSpecifier = Objects.requireNonNull(alcGetString(NULL, ALC_DEFAULT_DEVICE_SPECIFIER));
                System.out.println("Default device: " + defaultDeviceSpecifier);

                System.out.println("ALC device specifier: " + alcGetString(device, ALC_DEVICE_SPECIFIER));

                final long context = alcCreateContext(device, (IntBuffer)null);
                checkALCError(device);

                boolean useTLC = deviceCaps.ALC_EXT_thread_local_context && alcSetThreadContext(context);
                if (!useTLC) {
                    if (!alcMakeContextCurrent(context)) {
                        throw new IllegalStateException();
                    }
                }
                checkALCError(device);

                ALCapabilities caps = AL.createCapabilities(deviceCaps, MemoryUtil::memCallocPointer);
                AL.setCurrentProcess(caps);
                // 创建 OpenAL 缓冲区
                bufferId = AL10.alGenBuffers();
            Frame frame;
            double playSoundTimeMillis = -1;
            synchronized (FuncPlayvid.this){
                FuncPlayvid.this.notify();
            }
            grabber.start();
            while (( frame = grabber.grabSamples()) != null) {
                try {
                    int sourceId = 0;
                    if (frame.type == Frame.Type.AUDIO) {

                        Buffer buffer = frame.samples[0];
                        if (buffer instanceof ByteBuffer)
                            AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO16, ((ByteBuffer) buffer), sampleRate);
                        else if (buffer instanceof ShortBuffer)
                            AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO16, ((ShortBuffer) buffer), sampleRate);
                        else if (buffer instanceof FloatBuffer)
                            AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO16, ((FloatBuffer) buffer), sampleRate);
                        else throw new RuntimeException("unknown format");
                        // 创建 OpenAL 源
                        sourceId = AL10.alGenSources();
                        AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);

                        // 播放音频
                        AL10.alSourcePlay(sourceId);
                    }

                    synchronized (this) {
                        if (frame.type == Frame.Type.AUDIO) {
                            if (playSoundTimeMillis==-1) {
                                // 记录指令开始时间
                                long startTime = System.nanoTime();
                                while (AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {

                                }
                                // 记录指令结束时间
                                long endTime = System.nanoTime();

                                // 计算指令执行时间（以纳秒为单位）
                                long executionTime = endTime - startTime;

                                // 将纳秒转换为毫秒
                                playSoundTimeMillis = executionTime / 1_000_000.0;
                            }else {
                                Thread.sleep((long) playSoundTimeMillis);
                                while (AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {

                                }
                            }
                        }
                    }
                    // 等待音频播放结束

                    // 删除 OpenAL 源
                    AL10.alSourceStop(sourceId);
                    AL10.alDeleteSources(sourceId);


                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }// 删除 OpenAL 缓冲区
                AL10.alDeleteBuffers(bufferId);
                ALC.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    static void checkALCError(long device) {
        int err = alcGetError(device);
        if (err != ALC_NO_ERROR) {
            throw new RuntimeException(alcGetString(device, err));
        }
    }
    byte[] combine;
    private void processAudio(Buffer[] samples) {
        combine=new byte[samples[0].remaining()];
        int i=0;
        ByteArrayOutputStream bas;
        bas=new ByteArrayOutputStream();
        while (samples[0].hasRemaining()){
            if (samples[0] instanceof ByteBuffer)
                bas.write(((ByteBuffer) samples[0]).get());
            if (samples[0] instanceof ShortBuffer)
                bas.write(((ShortBuffer) samples[0]).get());
            i++;
        }
        sourceDataLine.write(bas.toByteArray(), 0,bas.size());
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
    private String[] args;
    @Override
    public void run(String[] args) {
        this.args=args;
        say("playvid is starting");
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
        say("pos was sat");
        for (BlockPos block : blocks) {
            try {
                Main.baseTelnet.send(String.format("/setblock %d %d %d minecraft:%s", block.x,block.y,block.z,args[7]));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        say("block filled");
        generator.getMaterialSet().setAll();
        generator.setSideView(false);
        say("change to working thread");
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
