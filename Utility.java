import java.io.*;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import java.awt.Color;

public class Utility {

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
        double maxLoss = 20.0;

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

        StringBuilder sb = new StringBuilder();
        traverseQuadTree(root, huffmanCodes, sb);

        byte[] compressedImage = createByteArrayFromStringBuilder(sb);

        // Now we encode the huffman tree
        int treeSize = huffmanCodes.size();
        List<Map.Entry<Color, String>> codes = new ArrayList<Map.Entry<Color, String>>(huffmanCodes.entrySet());


        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputFileName)))) {
            dos.writeInt(pixels.length);
            dos.writeInt(pixels[0].length);

            dos.writeInt(treeSize);
            for (int i = 0; i < codes.size(); i++) {
                Map.Entry<Color, String> huffmanCode = codes.get(i);
               
                dos.writeByte(huffmanCode.getKey().getRed());
                dos.writeByte(huffmanCode.getKey().getGreen());
                dos.writeByte(huffmanCode.getKey().getBlue());

                dos.writeShort(huffmanCode.getValue().length());

                sb.setLength(0);
                sb.append(huffmanCode.getValue());
                dos.write(
                    createByteArrayFromStringBuilder(sb)
                );
            }

            dos.write(compressedImage);

        } catch (IOException e) {
            System.err.println("An I/O error occurred while writing the compressed data: " + e.getMessage());
        }
    }

    private byte[] createByteArrayFromStringBuilder(StringBuilder sb) {
        int paddingLength = sb.length() % 8;
        if (paddingLength != 0) {
            for (int i = 0; i < 8 - paddingLength; i++) {
                sb.append("0");
            }
        }

        String byteString = sb.toString();
        byte[] arr = new byte[byteString.length() / 8];
        for (int i = 0; i < byteString.length() / 8; i++) {
            for (int j = 0; j < 8; j++) {
                arr[i] <<= 1;
                arr[i] |= byteString.charAt(i * 8 + j) - '0';
            }
        }

        return arr;
    }

    private String getStringFromByteArray(StringBuilder sb, byte[] arr) {
        // Convert byte array to String
        sb.setLength(0);
        for (byte b : arr) {
            for (int i = 7; i >= 0; i--) {
                sb.append((b >> i) & 1);
            }
        }

        return sb.toString();
    }

    private String getCodeStringFromByteArray(StringBuilder sb, byte[] arr, int codeLength) {
        sb.setLength(0);
        for (byte b : arr) {
            for (int i = 7; i >= 0; i--) {
                sb.append((b >> i) & 1);
            }
        }
        sb.setLength(codeLength);
        return sb.toString();
    }

    public int[][][] Decompress(String inputFileName) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(inputFileName)))) {
            try {
                int width = dis.readInt();
                int height = dis.readInt();

                int[][][] pixels = new int[width][height][3];

                // Build huffman tree
                HuffmanNode huffmanRoot = new HuffmanNode(0, null);
                int treeSize = dis.readInt();
                for (int i = 0; i < treeSize; i++) {
                    int red = dis.readByte() & 0xFF;
                    int green = dis.readByte() & 0xFF;
                    int blue = dis.readByte() & 0xFF;

                    int codeLength = dis.readShort();
                    int codeLengthBytes = (int) Math.ceil(codeLength / 8.0);

                    String code = getCodeStringFromByteArray(sb, dis.readNBytes(codeLengthBytes), codeLength);
                    HuffmanNode curr = huffmanRoot;
                    for (int j = 0; j < code.length(); j++) {
                        if (code.charAt(j) == '0') {
                            if (curr.left == null) {
                                curr.left = new HuffmanNode(0, null);
                            }
                            curr = curr.left;
                        } else {
                            if (curr.right == null) {
                                curr.right = new HuffmanNode(0, null);
                            }
                            curr = curr.right;
                        }
                    }

                    curr.color = new Color(red, green, blue);
                }

                String byteString = getStringFromByteArray(sb, dis.readAllBytes());
                Queue<Character> queue = new LinkedList<Character>();
                for (char c : byteString.toCharArray()) {
                    queue.add(c);
                }

                decompressQuadTree(pixels, 0, 0, width, height, huffmanRoot, queue);

                return pixels;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private void decompressQuadTree(int[][][] pixels, int xStart, int yStart, int width, int height, HuffmanNode huffmanRoot, Queue<Character> queue) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;

        int[][] offsets = {
                { 0, 0 }, { halfWidth, 0 }, { 0, halfHeight }, { halfWidth, halfHeight }
        };

        char value = queue.poll();
        if (value == '0') {
            for (int i = 0; i < 4; i++) {
                int xOff = offsets[i][0];
                int yOff = offsets[i][1];
                int w = (i % 2 == 0) ? halfWidth : width - halfWidth;
                int h = (i < 2) ? halfHeight : height - halfHeight;
                
                decompressQuadTree(pixels, xStart + xOff, yStart + yOff, w, h, huffmanRoot, queue);
            }
        } else {
            // Traverse huffman until end
            HuffmanNode curr = huffmanRoot;

            boolean done = false;
            while (!done) {
                boolean leafNodeReached = false;
                char direction;
                if (queue.size() == 0) leafNodeReached = true;
                else {
                    direction = queue.peek();
                    if (
                        (direction == '0' && curr.left == null) ||
                        (direction == '1' && curr.right == null)
                    ) leafNodeReached = true;
                }
                
                if (leafNodeReached) {
                    // We have reached a leaf node
                    done = true;
                    for (int x = xStart; x < Math.min(xStart + width, pixels.length); x++) {
                        for (int y = yStart; y < Math.min(yStart + height, pixels[0].length); y++) {
                            pixels[x][y][0] = curr.color.getRed();
                            pixels[x][y][1] = curr.color.getGreen();
                            pixels[x][y][2] = curr.color.getBlue();
                        }
                    }
                } else {
                    direction = queue.poll();
                    if (direction == '0') {
                        curr = curr.left;
                    } else {
                        curr = curr.right;
                    }
                }
            }
        }
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

    private void traverseQuadTree(QuadNode node, Map<Color, String> huffmanCodes, StringBuilder sb) {
        if (node.isLeaf) {
            String huffmanCode = huffmanCodes.get(node.color);
            sb.append("1" + huffmanCode);
            return;
        }

        sb.append("0");
        for (int i = 0; i < 4; i++) {
            traverseQuadTree(node.children[i], huffmanCodes, sb);
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
}
