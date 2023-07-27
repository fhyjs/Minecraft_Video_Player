package org.eu.hanana.reimu.mcvp;

import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;

import java.nio.ByteBuffer;

public class ReimuVideoSurface extends VideoSurface  implements RenderCallback{
    /**
     * 创建一个新的包装器.
     *
     *
     */
    protected ReimuVideoSurface() {
        super(new ReimuVideoSurface.videoSurfaceAdapter());
    }

    @Override
    public void attach(MediaPlayer mediaPlayer) {

    }

    @Override
    public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat) {

    }

    public static class videoSurfaceAdapter implements VideoSurfaceAdapter {

        @Override
        public void attach(MediaPlayer mediaPlayer, long componentId) {

        }
    }
}
