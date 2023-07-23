package org.eu.hanana.reimu.mcvp;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.bytedeco.opencv.global.opencv_core.flip;

public class Glutil {
    // 分割图像
    public static List<BufferedImage> splitImage(BufferedImage inputImage, int numParts) {
        int width = inputImage.getWidth();
        int height = inputImage.getHeight();
        int partWidth = width / numParts;
        List<BufferedImage> subImages = new ArrayList<>();

        for (int i = 0; i < numParts; i++) {
            int x = i * partWidth;
            int w = (i == numParts - 1) ? width - x : partWidth;

            BufferedImage subImage = inputImage.getSubimage(x, 0, w, height);
            subImages.add(subImage);
        }

        return subImages;
    }
    // 合并图像
    public static BufferedImage mergeImages(List<Future<BufferedImage>> compressedSubImages, int width, int height) throws ExecutionException, InterruptedException {
        BufferedImage outputImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int currentX = 0;

        for (Future<BufferedImage> future : compressedSubImages) {
            BufferedImage compressedImage = future.get();
            int compressedWidth = compressedImage.getWidth();
            int compressedHeight = compressedImage.getHeight();

            outputImage.createGraphics().drawImage(compressedImage, currentX, 0, null);
            currentX += compressedWidth;
        }

        return outputImage;
    }
    public static Frame flipFrame(Frame frame, boolean flipHorizontal, boolean flipVertical) {
        if (frame == null) {
            return null; // 添加对空帧的处理，返回空帧或抛出异常，具体根据业务需要进行处理
        }
        Mat mat = new OpenCVFrameConverter.ToMat().convert(frame);
        int flipCode = (flipHorizontal && flipVertical) ? -1 : (flipHorizontal ? 1 : (flipVertical ? 0 : 0));
        flip(mat, mat, flipCode);
        return new OpenCVFrameConverter.ToOrgOpenCvCoreMat().convert(mat);
    }
}
