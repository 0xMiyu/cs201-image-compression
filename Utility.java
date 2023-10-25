import java.io.*;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import java.awt.Color;

public class Utility implements Serializable {
    private static final long serialVersionUID = 3L;

    class PackedData implements Serializable {
        int width, height;
        HuffmanNode root;
        Queue<String> compressedImage;

        public PackedData(int width, int height, HuffmanNode root, Queue<String> compressedImage) {
            this.width = width;
            this.height = height;
            this.root = root;
            this.compressedImage = compressedImage;
        }
    }

    class QuadNode {
        int x, y, width, height;
        Color color;
        boolean isLeaf;
        QuadNode[] children;

        public QuadNode(int x, int y, int width, int height, Color color, boolean isLeaf) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.isLeaf = isLeaf;
            this.children = new QuadNode[4];
        }
    }

    class HuffmanNode implements Comparable<HuffmanNode>, Serializable {
        int freq;
        Color color;
        HuffmanNode left, right;
    
        public HuffmanNode(int freq, Color color) {
            this.freq = freq;
            this.color = color;
            left = right = null;
        }
    
        public int compareTo(HuffmanNode other) {
            return this.freq - other.freq;
        }
    }

    class HuffmanCoding {
        
        public Map<Color, Integer> frequencies = new HashMap<Color, Integer>();
        public PriorityQueue<HuffmanNode> pq = new PriorityQueue<HuffmanNode>();

        public HuffmanNode buildPriorityQueue() {
            for (Color color : frequencies.keySet()) {
                pq.add(new HuffmanNode(frequencies.get(color), color));
            }

            while (pq.size() > 1) {
                HuffmanNode left = pq.poll();
                HuffmanNode right = pq.poll();

                HuffmanNode merged = new HuffmanNode(left.freq + right.freq, null);
                merged.left = left;
                merged.right = right;

                pq.add(merged);
            }

            return pq.poll();
        }

        public void generateHuffmanCodes(HuffmanNode root, String code, HashMap<Color, String> huffmanCodes) {
            if (root == null) {
                return;
            }
    
            if (root.color != null) {
                huffmanCodes.put(root.color, code);
            }
    
            generateHuffmanCodes(root.left, code + "0", huffmanCodes);
            generateHuffmanCodes(root.right, code + "1", huffmanCodes);
        }
    }

    class DecompressQuadNode {
        public int x, y, width, height, i;

        public DecompressQuadNode(int x, int y, int width, int height, int i) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.i = i;
        }
    }

    public void Compress(int[][][] pixels, String outputFileName) {
        int minDepth = 7;
        int maxDepth = 10;
        double maxLoss = 15.0;

        QuadNode root = null;
        int[][][] preproccessedPixels = getPreprocessedPixels(pixels);

        HuffmanCoding hc = new HuffmanCoding();

        // Using the buildQuadtreeWrapper with the parameters
        try {
            root = buildQuadtreeWrapper(pixels, preproccessedPixels, maxLoss, minDepth, maxDepth, hc);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            System.err.println("Compression was interrupted: " + e.getMessage());
            return; // Exit the method early
        } catch (ExecutionException e) {
            System.err.println("An error occurred during compression: " + e.getMessage());
            e.printStackTrace(); // This will print the root cause of the exception
            return; // Exit the method early
        }

        HuffmanNode huffmanRoot = hc.buildPriorityQueue();

        HashMap<Color, String> huffmanCodes = new HashMap<>();
        hc.generateHuffmanCodes(huffmanRoot, "", huffmanCodes);

        Queue<String> queue = new LinkedBlockingQueue<String>();

        traverseQuadTree(root, huffmanCodes, queue);

        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(outputFileName)))) {
            // Write the dimensions of the image
            oos.writeObject(
                new PackedData(
                    pixels.length, 
                    pixels[0].length, 
                    huffmanRoot, 
                    queue
                )
            );
        } catch (IOException e) {
            System.err.println("An I/O error occurred while writing the compressed data: " + e.getMessage());
        }
    }

    private void decompressQuadTree(int[][][] pixels, int xStart, int yStart, int width, int height, HuffmanNode huffmanRoot, Queue<String> al) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;

        int[][] offsets = {
                { 0, 0 }, { halfWidth, 0 }, { 0, halfHeight }, { halfWidth, halfHeight }
        };

        String value = al.poll();
        if (value.equals(".")) {
            for (int i = 0; i < 4; i++) {
                int xOff = offsets[i][0];
                int yOff = offsets[i][1];
                int w = (i % 2 == 0) ? halfWidth : width - halfWidth;
                int h = (i < 2) ? halfHeight : height - halfHeight;
                
                decompressQuadTree(pixels, xStart + xOff, yStart + yOff, w, h, huffmanRoot, al);
            }
        } else {
            // Traverse huffman
            HuffmanNode curr = huffmanRoot;
            for (int i = 0; i < value.length(); i++) {
                if (value.charAt(i) == '0') {
                    curr = curr.left;
                } else {
                    curr = curr.right;
                }
            }

            for (int x = xStart; x < Math.min(xStart + width, pixels.length); x++) {
                for (int y = yStart; y < Math.min(yStart + height, pixels[0].length); y++) {
                    pixels[x][y][0] = curr.color.getRed();
                    pixels[x][y][1] = curr.color.getGreen();
                    pixels[x][y][2] = curr.color.getBlue();
                }
            }
        }
    }

    public int[][][] Decompress(String inputFileName) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(inputFileName)))) {
            try {
                PackedData data = (PackedData) ois.readObject();

                int[][][] pixels = new int[data.width][data.height][3];
                decompressQuadTree(pixels, 0, 0, data.width, data.height, data.root, data.compressedImage);

                return pixels;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private int[][][] getPreprocessedPixels(int[][][] pixels) {
        int width = pixels.length;
        int height = pixels[0].length;

        int[][][] preprocessedPixels = new int[width][height][3];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                preprocessedPixels[x][y][0] = pixels[x][y][0];
                preprocessedPixels[x][y][1] = pixels[x][y][1];
                preprocessedPixels[x][y][2] = pixels[x][y][2];
    
                if (x > 0) {
                    preprocessedPixels[x][y][0] += preprocessedPixels[x - 1][y][0];
                    preprocessedPixels[x][y][1] += preprocessedPixels[x - 1][y][1];
                    preprocessedPixels[x][y][2] += preprocessedPixels[x - 1][y][2];
                }
    
                if (y > 0) {
                    preprocessedPixels[x][y][0] += preprocessedPixels[x][y - 1][0];
                    preprocessedPixels[x][y][1] += preprocessedPixels[x][y - 1][1];
                    preprocessedPixels[x][y][2] += preprocessedPixels[x][y - 1][2];
                }
    
                if (x > 0 && y > 0) {
                    preprocessedPixels[x][y][0] -= preprocessedPixels[x - 1][y - 1][0];
                    preprocessedPixels[x][y][1] -= preprocessedPixels[x - 1][y - 1][1];
                    preprocessedPixels[x][y][2] -= preprocessedPixels[x - 1][y - 1][2];
                }
            }
        }
        
        return preprocessedPixels;
    }

    private Color averageColor(int[][][] preprocessedPixels, int xStart, int yStart, int width, int height) {
        if (width == 0 || height == 0) {
            return new Color(0, 0, 0);
        }

        int xEnd = xStart + width - 1;
        int yEnd = yStart + height - 1;
    
        int totalRed = preprocessedPixels[xEnd][yEnd][0];
        int totalGreen = preprocessedPixels[xEnd][yEnd][1];
        int totalBlue = preprocessedPixels[xEnd][yEnd][2];
    
        if (xStart > 0) {
            totalRed -= preprocessedPixels[xStart - 1][yEnd][0];
            totalGreen -= preprocessedPixels[xStart - 1][yEnd][1];
            totalBlue -= preprocessedPixels[xStart - 1][yEnd][2];
        }
    
        if (yStart > 0) {
            totalRed -= preprocessedPixels[xEnd][yStart - 1][0];
            totalGreen -= preprocessedPixels[xEnd][yStart - 1][1];
            totalBlue -= preprocessedPixels[xEnd][yStart - 1][2];
        }
    
        if (xStart > 0 && yStart > 0) {
            totalRed += preprocessedPixels[xStart - 1][yStart - 1][0];
            totalGreen += preprocessedPixels[xStart - 1][yStart - 1][1];
            totalBlue += preprocessedPixels[xStart - 1][yStart - 1][2];
        }
    
        int count = width * height;
    
        // Compute the average values for each channel
        int averageRed = totalRed / count;
        int averageGreen = totalGreen / count;
        int averageBlue = totalBlue / count;
    
        // Ensure the values are within the valid range [0, 255]
        int clampedRed = Math.min(255, Math.max(0, averageRed));
        int clampedGreen = Math.min(255, Math.max(0, averageGreen));
        int clampedBlue = Math.min(255, Math.max(0, averageBlue));
    
        return new Color(clampedRed, clampedGreen, clampedBlue);
    }

    private QuadNode buildQuadtreeWrapper(int[][][] pixels, int[][][] preproccessedPixels, double maxLoss, int minDepth, int maxDepth, HuffmanCoding hc)
        throws InterruptedException, ExecutionException {

        int xStart = 0;
        int yStart = 0;
        int width = pixels.length;
        int height = pixels[0].length;

        // int numCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(1000);

        // ExecutorService executor = Executors.newCachedThreadPool();;

        QuadNode root = buildQuadtree(pixels, preproccessedPixels, xStart, yStart, width, height, maxLoss, minDepth, maxDepth, executor, 1, hc); // start at depth 1

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        return root;
    }

    private static final int MAX_CONCURRENT_DEPTH = 0; // Adjust as needed

    private void traverseQuadTree(QuadNode node, Map<Color, String> huffmanCodes, Queue<String> queue) {
        if (node.isLeaf) {
            String huffmanCode = huffmanCodes.get(node.color);
            queue.add(huffmanCode);
            return;
        }

        queue.add(".");
        for (int i = 0; i < 4; i++) {
            traverseQuadTree(node.children[i], huffmanCodes, queue);
        }
    }

    private QuadNode buildQuadtree(int[][][] pixels, int[][][] preproccessedPixels, int xStart, int yStart, int width, int height,
        double lossThreshold, int minDepth, int maxDepth, ExecutorService executor, int currentDepth, HuffmanCoding hc)
        throws InterruptedException, ExecutionException {

        if (currentDepth >= minDepth) {
            Color avgColor = averageColor(preproccessedPixels, xStart, yStart, width, height);

            if ((currentDepth >= maxDepth || isCloseEnough(pixels, xStart, yStart, width, height, avgColor, lossThreshold))) {
                hc.frequencies.put(avgColor, hc.frequencies.getOrDefault(avgColor, 0) + 1);
                return new QuadNode(xStart, yStart, width, height, avgColor, true);
            }
        }

        int halfWidth = width / 2;
        int halfHeight = height / 2;

        QuadNode node = new QuadNode(xStart, yStart, width, height, null, false);

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
                futures[i] = executor.submit(() -> buildQuadtree(pixels, preproccessedPixels, xStart + xOff, yStart + yOff, w, h, lossThreshold, 
                            minDepth, maxDepth, executor, currentDepth + 1, hc));
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
                node.children[i] = buildQuadtree(pixels, preproccessedPixels, xStart + xOff, yStart + yOff, w, h, lossThreshold,
                    minDepth, maxDepth, executor, currentDepth + 1, hc);

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
            return new QuadNode(x, y, width, height, color, true);
        } else {
            QuadNode node = new QuadNode(x, y, width, height, null, false);
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
