import java.io.*;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Stack;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Node;

import java.awt.Color;

public class Utility implements Serializable {
    private static final long serialVersionUID = 3L;

    class SecretSauce {
        private static int position = 0;
        private static byte currentByte = 0;
        private static ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public static void packBit(int bit) {
            if (bit != 0 && bit != 1) {
                throw new IllegalArgumentException("Bit value should be 0 or 1.");
            }
            
            currentByte = (byte) (currentByte | (bit << (7 - position)));
            position++;

            if (position == 8) {
                baos.write(currentByte);
                currentByte = 0;
                position = 0;
            }
        }

        public static void flush() {
            while (position != 0) {
                packBit(0);
            }
        }

        public static byte[] getBytes() {
            return baos.toByteArray();
        }

        public static void reset() {
            position = 0;
            currentByte = 0;
            baos.reset();
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

    class HuffmanNode implements Comparable<HuffmanNode> {
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
        int minDepth = 3;
        int maxDepth = 8;
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
        hc.generateHuffmanCodes(huffmanRoot, "1", huffmanCodes);
        traverseQuadTree(root, huffmanCodes);

        SecretSauce.flush();
        byte[] compressedImage = SecretSauce.getBytes();

        SecretSauce.reset();

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFileName)))) {
            // Write the dimensions of the image
            dos.writeInt(pixels.length);
            dos.writeInt(pixels[0].length);

            dos.writeShort(huffmanCodes.size());

            for (Color color : huffmanCodes.keySet()) {
                dos.write((byte) color.getRed());
                dos.write((byte) color.getGreen());
                dos.write((byte) color.getBlue());

                String code = huffmanCodes.get(color);
                dos.writeShort(code.length());
                for (int i = 0; i < code.length(); i++) {
                    SecretSauce.packBit(code.charAt(i) - '0');
                }

                SecretSauce.flush();
                dos.write(SecretSauce.getBytes());
                SecretSauce.reset();
            }

            dos.write(compressedImage);

            SecretSauce.flush();
            SecretSauce.reset();

            //serializeQuadNode(dos, root);
        } catch (IOException e) {
            System.err.println("An I/O error occurred while writing the compressed data: " + e.getMessage());
        }

        SecretSauce.reset();
    }

    public int[][][] Decompress(String inputFileName) throws IOException {
        int width, height;
        
        HuffmanNode huffmanRoot = new HuffmanNode(0, null);

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(inputFileName)))) {
            // Read the dimensions of the image
            width = dis.readInt();
            height = dis.readInt();

            int[][][] pixels = new int[width][height][3];

            int numCodes = dis.readShort();

            for (int i = 0; i < numCodes; i++) {
                int red = dis.readByte() & 0xFF;
                int green = dis.readByte() & 0xFF;
                int blue = dis.readByte() & 0xFF;
    
                int bitLength = dis.readShort() - 1;
                int byteLength = (int) Math.ceil(bitLength / 8.0);

                byte[] code = new byte[byteLength];
                for (int j = 0; j < byteLength; j++) {
                    code[j] = dis.readByte();
                }

                StringBuilder bitString = new StringBuilder();
                for (byte b : code) {
                    for (int j = 7; j >= 0; j--) {
                        bitString.append((b >> j) & 1);
                        bitLength--;

                        if (bitLength == 0) {
                            break;
                        }
                    }

                    if (bitLength == 0) {
                        break;
                    }
                }
                
                HuffmanNode curr = huffmanRoot;
                for (int j = 1; j < bitString.length(); j++) {
                    char c = bitString.charAt(j);
                    if (c == '1') {
                        if (curr.right == null) {
                            curr.right = new HuffmanNode(0, new Color(red, green, blue));
                            break;
                        }
                        curr = curr.right;
                    } else {
                        if (curr.left == null) {
                            curr.left = new HuffmanNode(0, new Color(red, green, blue));
                            break;
                        }
                        curr = curr.left;
                    }
                }
            }

            byte[] bytes = dis.readAllBytes();

            StringBuilder bitString = new StringBuilder();
            for (byte b : bytes) {
                for (int i = 7; i >= 0; i--) {
                    bitString.append((b >> i) & 1);
                }
            }

            char[] huffmanCodes = bitString.toString().toCharArray();
            int i = 0;

            int end = decompressQuadTree(pixels, 0, 0, width, height, huffmanRoot, huffmanCodes, i);
            System.out.println(end + " " + huffmanCodes.length);
            return pixels;
        }
    }

    private int decompressQuadTree(int[][][] pixels, int xStart, int yStart, int width, int height, HuffmanNode huffmanRoot, char[] huffmanCodes, int i) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;

        // This is a leaf node
        if (huffmanCodes[i] == '1') {
            HuffmanNode curr = huffmanRoot;
            HuffmanNode prev = null;
            i += 1;

            while (curr != null) {
                prev = curr;
                if (huffmanCodes[i] == '1') {
                    curr = curr.right;
                } else {
                    curr = curr.left;
                }

                i += 1;
            }

            for (int x = xStart; x < Math.min(xStart + width, pixels.length); x++) {
                for (int y = yStart; y < Math.min(yStart + height, pixels[0].length); y++) {
                    pixels[x][y][0] = prev.color.getRed();
                    pixels[x][y][1] = prev.color.getGreen();
                    pixels[x][y][2] = prev.color.getBlue();
                }
            }

            return i - 1;
        }

        int[][] offsets = {
            { 0, 0 }, { halfWidth, 0 }, { 0, halfHeight }, { halfWidth, halfHeight }
        };

        for (int j = 0; j < 4; j++) {
            int xOff = offsets[j][0];
            int yOff = offsets[j][1];
            int w = (i % 2 == 0) ? halfWidth : width - halfWidth;
            int h = (i < 2) ? halfHeight : height - halfHeight;
            
            i = decompressQuadTree(pixels, xStart + xOff, yStart + yOff, w, h, huffmanRoot, huffmanCodes, i + 1);
        }

        return i + 1;
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

    private void traverseQuadTree(QuadNode node, Map<Color, String> huffmanCodes) {
        if (node.isLeaf) {
            String huffmanCode = huffmanCodes.get(node.color);
            
            SecretSauce.packBit(1);
            for (int i = 0; i < huffmanCode.length(); i++) {
                SecretSauce.packBit(huffmanCode.charAt(i) - '0');
            }

            return;
        }

        SecretSauce.packBit(0);
        for (int i = 0; i < 4; i++) {
            traverseQuadTree(node.children[i], huffmanCodes);
        }
    }

    private QuadNode buildQuadtree(int[][][] pixels, int[][][] preproccessedPixels, int xStart, int yStart, int width, int height,
        double lossThreshold, int minDepth, int maxDepth, ExecutorService executor, int currentDepth, HuffmanCoding hc)
        throws InterruptedException, ExecutionException {

        if (currentDepth >= minDepth) {
            Color avgColor = averageColor(preproccessedPixels, xStart, yStart, width, height);

            if ((currentDepth >= maxDepth || isCloseEnough(pixels, xStart, yStart, width, height, avgColor, lossThreshold))) {
                SecretSauce.packBit(1);
                hc.frequencies.put(avgColor, hc.frequencies.getOrDefault(avgColor, 0) + 1);
                return new QuadNode(xStart, yStart, width, height, avgColor, true);
            }
        }
        
        SecretSauce.packBit(0);

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
