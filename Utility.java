import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.awt.Color;

public class Utility implements Serializable {
    private static final long serialVersionUID = 3L;

    class QuadNode {
        int x, y, width, height;
        Color color;
        QuadNode[] children;

        public QuadNode(int x, int y, int width, int height, Color color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.children = new QuadNode[4];
        }
    }

    public void Compress(int[][][] pixels, String outputFileName) {
        int minDepth = 5;
        int maxDepth = 7;
        double maxLoss = 15.0;

        QuadNode root = null;

        // Using the buildQuadtreeWrapper with the parameters
        try {
            root = buildQuadtreeWrapper(pixels, maxLoss, maxDepth, minDepth);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            System.err.println("Compression was interrupted: " + e.getMessage());
            return; // Exit the method early
        } catch (ExecutionException e) {
            System.err.println("An error occurred during compression: " + e.getMessage());
            e.printStackTrace(); // This will print the root cause of the exception
            return; // Exit the method early
        }


        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(outputFileName))) {
            // Write the dimensions of the image
            dos.writeInt(pixels.length);
            dos.writeInt(pixels[0].length);

            serializeQuadNode(dos, root);
        } catch (IOException e) {
            System.err.println("An I/O error occurred while writing the compressed data: " + e.getMessage());
        }
    }

    public int[][][] Decompress(String inputFileName) throws IOException {
        int width, height;

        try (DataInputStream dis = new DataInputStream(new FileInputStream(inputFileName))) {
            // Read the dimensions of the image
            width = dis.readInt();
            height = dis.readInt();

            int[][][] pixels = new int[width][height][3];

            QuadNode root = deserializeQuadNode(dis);
            reconstructImageFromQuadtree(pixels, root);

            return pixels;
        }
    }

    // private boolean isHomogeneous(int[][][] pixels, int xStart, int yStart, int
    // width, int height) {
    // int firstPixel = pixels[xStart][yStart][0];
    // for (int x = xStart; x < Math.min(pixels.length, xStart + width); x++) {
    // for (int y = yStart; y < Math.min(pixels[0].length, yStart + height); y++) {
    // if (pixels[x][y][0] != firstPixel) {
    // return false;
    // }
    // }
    // }
    // return true;
    // }

    private Color averageColor(int[][][] pixels, int xStart, int yStart, int width, int height) {
        long totalRed = 0;
        long totalGreen = 0;
        long totalBlue = 0;
        int count = 0;
        for (int x = xStart; x < Math.min(pixels.length, xStart + width); x++) {
            for (int y = yStart; y < Math.min(pixels[0].length, yStart + height); y++) {
                totalRed += pixels[x][y][0];
                totalGreen += pixels[x][y][1];
                totalBlue += pixels[x][y][2];
                count++;
            }
        }

        if (count == 0) {
            return new Color(0, 0, 0); // or any other default color or handling
        }

        int avgRed = (int) (totalRed / count);
        int avgGreen = (int) (totalGreen / count);
        int avgBlue = (int) (totalBlue / count);

        return new Color(avgRed, avgGreen, avgBlue);
    }

    public QuadNode buildQuadtreeWrapper(int[][][] pixels, double maxLoss, int maxDepth, int minDepth)
        throws InterruptedException, ExecutionException {

        int xStart = 0;
        int yStart = 0;
        int width = pixels.length;
        int height = pixels[0].length;

        // int numCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(1000);

        // ExecutorService executor = Executors.newCachedThreadPool();;

        QuadNode root = buildQuadtree(pixels, xStart, yStart, width, height, maxLoss, maxDepth, minDepth, executor, 1); // start at depth 1

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        return root;
    }

    private static final int MAX_CONCURRENT_DEPTH = 0; // Adjust as needed

    private QuadNode buildQuadtree(int[][][] pixels, int xStart, int yStart, int width, int height,
        double lossThreshold, int maxDepth, int minDepth, ExecutorService executor, int currentDepth)
        throws InterruptedException, ExecutionException {

        Color avgColor = averageColor(pixels, xStart, yStart, width, height);

        if ((currentDepth == maxDepth || isCloseEnough(pixels, xStart, yStart, width, height, avgColor, lossThreshold)) &&
        currentDepth >= minDepth) {
            return new QuadNode(xStart, yStart, width, height, avgColor);
        }

        int halfWidth = width / 2;
        int halfHeight = height / 2;

        QuadNode node = new QuadNode(xStart, yStart, width, height, null);

        int[][] offsets = {
                { 0, 0 }, { halfWidth, 0 }, { 0, halfHeight }, { halfWidth, halfHeight }
        };

        if (currentDepth <= MAX_CONCURRENT_DEPTH) {
            Future<QuadNode>[] futures = new Future[4];
            for (int i = 0; i < 4; i++) {
                int xOff = offsets[i][0];
                int yOff = offsets[i][1];
                int w = (i % 2 == 0) ? halfWidth : width - halfWidth;
                int h = (i < 2) ? halfHeight : height - halfHeight;
                futures[i] = executor.submit(() -> buildQuadtree(pixels, xStart + xOff, yStart + yOff, w, h, lossThreshold, 
                            maxDepth, minDepth, executor, currentDepth + 1));
            }

            for (int i = 0; i < 4; i++) {
                node.children[i] = futures[i].get();
            }
        } else {
            for (int i = 0; i < 4; i++) {
                int xOff = offsets[i][0];
                int yOff = offsets[i][1];
                int w = (i % 2 == 0) ? halfWidth : width - halfWidth;
                int h = (i < 2) ? halfHeight : height - halfHeight;
                node.children[i] = buildQuadtree(pixels, xStart + xOff, yStart + yOff, w, h, lossThreshold,
                    maxDepth, minDepth, executor, currentDepth + 1);

            }
        }

        return node;
    }

    private boolean isCloseEnough(int[][][] pixels, int xStart, int yStart, int width, int height, Color avgColor,
            double lossThreshold) {
        int count = 0;
        for (int x = xStart; x < Math.min(xStart + width, pixels.length); x++) {
            for (int y = yStart; y < Math.min(yStart + height, pixels[0].length); y++) {
                Color currentColor = new Color(pixels[x][y][0], pixels[x][y][1], pixels[x][y][2]);
                double distance = Math.sqrt(
                        Math.pow(currentColor.getRed() - avgColor.getRed(), 2) +
                                Math.pow(currentColor.getGreen() - avgColor.getGreen(), 2) +
                                Math.pow(currentColor.getBlue() - avgColor.getBlue(), 2));
                if (distance <= lossThreshold * 255 / 100) {
                    count++;
                }
            }
        }
        return (double) count / (width * height) >= 0.9; // change this to say if 90% and above then good enough
    }

    private void serializeQuadNode(DataOutputStream dos, QuadNode node) throws IOException {
        dos.writeInt(node.x);
        dos.writeInt(node.y);
        dos.writeInt(node.width);
        dos.writeInt(node.height);

        if (node.color != null) {
            dos.writeBoolean(true);
            dos.writeInt(node.color.getRGB());
        } else {
            dos.writeBoolean(false);
            for (int i = 0; i < 4; i++) {
                serializeQuadNode(dos, node.children[i]);
            }
        }
    }

    private QuadNode deserializeQuadNode(DataInputStream dis) throws IOException {
        int x = dis.readInt();
        int y = dis.readInt();
        int width = dis.readInt();
        int height = dis.readInt();

        boolean isLeaf = dis.readBoolean();
        if (isLeaf) {
            Color color = new Color(dis.readInt());
            return new QuadNode(x, y, width, height, color);
        } else {
            QuadNode node = new QuadNode(x, y, width, height, null);
            for (int i = 0; i < 4; i++) {
                node.children[i] = deserializeQuadNode(dis);
            }
            return node;
        }
    }

    private void reconstructImageFromQuadtree(int[][][] pixels, QuadNode node) {
        if (node.color != null) {
            int xEnd = Math.min(pixels.length, node.x + node.width);
            int yEnd = Math.min(pixels[0].length, node.y + node.height);

            for (int i = node.x; i < xEnd; i++) {
                for (int j = node.y; j < yEnd; j++) {
                    pixels[i][j][0] = node.color.getRed();
                    pixels[i][j][1] = node.color.getGreen();
                    pixels[i][j][2] = node.color.getBlue();
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                reconstructImageFromQuadtree(pixels, node.children[i]);
            }
        }
    }

}
