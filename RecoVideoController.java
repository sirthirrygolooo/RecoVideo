import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc; // Import nécessaire pour le traitement d'image
import org.opencv.videoio.VideoCapture;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Controller de l'app
 */
public class RecoVideoController
{
    // the FXML button
    @FXML
    private Button button;
    // the FXML ximage view
    @FXML
    private ImageView currentFrame;

    // a timer for acquiring the video stream
    private ScheduledExecutorService timer;
    // the OpenCV object that realizes the video capture
    private VideoCapture capture = new VideoCapture();
    // a flag to change the button behavior
    private boolean cameraActive = false;
    // the id of the camera to be used
    private static int cameraId = 0;

    private Mat backgroundFrame = null;

    private final Size BLUR_SIZE = new Size(21, 21);

    private final double THRESHOLD_VALUE = 35.0;

    private final int DILATE_PASSES = 3;

    private final double MIN_AREA_THRESHOLD = 3000;

    /**
     * The action triggered by pushing the button on the GUI
     *
     * @param event the push button event
     */
    @FXML
    protected void startCamera(ActionEvent event)
    {
        if (!this.cameraActive)
        {
            this.capture.open(cameraId);

            this.backgroundFrame = null;

            if (this.capture.isOpened())
            {
                this.cameraActive = true;

                Runnable frameGrabber = new Runnable() {

                    @Override
                    public void run()
                    {
                        Mat frame = grabFrame();
                        Image imageToShow = mat2Image(frame);
                        updateImageView(currentFrame, imageToShow);
                        // currentFrame.setFitWidth(800);
                        // currentFrame.setPreserveRatio(true);
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                // update the button content
                this.button.setText("Stop");
            }
            else
            {
                // log the error
                System.err.println("Impossible to open the camera connection...");
            }
        }
        else
        {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.button.setText("Start");

            // stop the timer
            this.stopAcquisition();
        }
    }

    public void findContours(int hehe) {
        // Méthode conservée mais non utilisée dans ce pipeline
    }

    /**
     * Get a frame from the opened video stream (if any) and applies Motion Detection
     *
     * @return the {@link Mat} to show
     */
    private Mat grabFrame()
    {
        // init everything
        Mat frame = new Mat();

        // check if the capture is open
        if (this.capture.isOpened())
        {
            try
            {
                // read the current frame
                this.capture.read(frame);

                // if the frame is not empty, process it
                if (!frame.empty())
                {
                    if (backgroundFrame == null) {
                        backgroundFrame = new Mat();
                        Imgproc.cvtColor(frame, backgroundFrame, Imgproc.COLOR_BGR2GRAY);
                        Imgproc.GaussianBlur(backgroundFrame, backgroundFrame, BLUR_SIZE, 0);
                        return frame;
                    }

                    Mat grayFrame = new Mat();
                    Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
                    Imgproc.GaussianBlur(grayFrame, grayFrame, BLUR_SIZE, 0);

                    Mat diffFrame = new Mat();
                    Core.absdiff(backgroundFrame, grayFrame, diffFrame);

                    Mat threshFrame = new Mat();
                    Imgproc.threshold(diffFrame, threshFrame, THRESHOLD_VALUE, 255, Imgproc.THRESH_BINARY);

                    for (int i = 0; i < DILATE_PASSES; i++) {
                        Imgproc.dilate(threshFrame, threshFrame, new Mat());
                    }

                    List<MatOfPoint> contours = new ArrayList<>();
                    Mat hierarchy = new Mat();
                    Imgproc.findContours(threshFrame, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

                    for (MatOfPoint contour : contours) {
                        double area = Imgproc.contourArea(contour);

                        if (area > MIN_AREA_THRESHOLD) {
                            Rect rect = Imgproc.boundingRect(contour);
                            Imgproc.rectangle(frame, rect.tl(), rect.br(), new Scalar(100, 255, 0), 2);
                        }
                    }

                    grayFrame.release();
                    diffFrame.release();
                    threshFrame.release();
                    hierarchy.release();
                }
            }
            catch (Exception e)
            {
                // log the error
                System.err.println("Exception during the image elaboration: " + e);
            }
        }

        return frame;
    }

    /**
     * Stop the acquisition from the camera and release all the resources
     */
    private void stopAcquisition()
    {
        if (this.timer!=null && !this.timer.isShutdown())
        {
            try
            {
                // stop the timer
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                // log any exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }
        }

        if (this.capture.isOpened())
        {
            // release the camera
            this.capture.release();
        }
    }

    /**
     * Update the {@link ImageView} in the JavaFX main thread
     */
    private void updateImageView(ImageView view, Image image)
    {
        onFXThread(view.imageProperty(), image);
    }

    /**
     * On application close, stop the acquisition from the camera
     */
    protected void setClosed()
    {
        this.stopAcquisition();
    }

    // Méthode alternative pour conversion (non utilisée ici)
    private Image matToJavaFXImage(Mat mat) {
        MatOfByte buffer = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buffer);
        return new Image(new java.io.ByteArrayInputStream(buffer.toArray()));
    }


    /**
     * Convert a Mat object (OpenCV) in the corresponding Image for JavaFX
     */
    public static Image mat2Image(Mat frame)
    {
        try
        {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        }
        catch (Exception e)
        {
            System.err.println("Cannot convert the Mat obejct: " + e);
            return null;
        }
    }

    /**
     * Support for the {@link mat2image()} method
     */
    private static BufferedImage matToBufferedImage(Mat original)
    {
        // init
        BufferedImage image = null;
        int width = original.width(), height = original.height(), channels = original.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        original.get(0, 0, sourcePixels);

        if (original.channels() > 1)
        {
            // BGR color image
            image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        }
        else
        {
            // Grayscale image
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        }
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);

        return image;
    }

    /**
     * Generic method for putting element running on a non-JavaFX thread on the
     * JavaFX thread, to properly update the UI
     */
    public static <T> void onFXThread(final ObjectProperty<T> property, final T value)
    {
        Platform.runLater(() -> {
            property.set(value);
        });
    }

}