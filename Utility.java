import java.io.*;
import java.awt.Color;

public class Utility implements Serializable {
    private static final long serialVersionUID = 3L;


    class QuadNode {
        int x, y, size;
        Color color;
        QuadNode[] children;

        public QuadNode(int x, int y, int size, Color color) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
            this.children = new QuadNode[4];
        }
    }

    public void Compress(int[][][] pixels, String outputFileName) throws IOException {
        QuadNode root = buildQuadtree(pixels, 0, 0, pixels[0].length, pixels.length, 20.0, 7); 
; // Assuming 20% loss and max depth of 5
    
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(outputFileName))) {
            // Write the dimensions of the image
            dos.writeInt(pixels.length);
            dos.writeInt(pixels[0].length);
            
            serializeQuadNode(dos, root);
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
    

    // private boolean isHomogeneous(int[][][] pixels, int xStart, int yStart, int width, int height) {
    //     int firstPixel = pixels[xStart][yStart][0];
    //     for (int x = xStart; x < Math.min(pixels.length, xStart + width); x++) {
    //         for (int y = yStart; y < Math.min(pixels[0].length, yStart + height); y++) {
    //             if (pixels[x][y][0] != firstPixel) {
    //                 return false;
    //             }
    //         }
    //     }
    //     return true;
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
    
    
    private QuadNode buildQuadtree(int[][][] pixels, int xStart, int yStart, int width, int height, double lossThreshold, int depth) {
        Color avgColor = averageColor(pixels, xStart, yStart, width, height);
        if (depth == 0 || isCloseEnough(pixels, xStart, yStart, width, height, avgColor, lossThreshold)) {
            return new QuadNode(xStart, yStart, Math.max(width, height), avgColor);
        }
    
        int halfWidth = width / 2;
        int halfHeight = height / 2;
    
        QuadNode node = new QuadNode(xStart, yStart, Math.max(width, height), null);
        node.children[0] = buildQuadtree(pixels, xStart, yStart, halfWidth, halfHeight, lossThreshold, depth - 1);
        node.children[1] = buildQuadtree(pixels, xStart + halfWidth, yStart, width - halfWidth, halfHeight, lossThreshold, depth - 1);
        node.children[2] = buildQuadtree(pixels, xStart, yStart + halfHeight, halfWidth, height - halfHeight, lossThreshold, depth - 1);
        node.children[3] = buildQuadtree(pixels, xStart + halfWidth, yStart + halfHeight, width - halfWidth, height - halfHeight, lossThreshold, depth - 1);
    
        return node;
    }
    
    private boolean isCloseEnough(int[][][] pixels, int xStart, int yStart, int width, int height, Color avgColor, double lossThreshold) {
        int count = 0;
        for (int x = xStart; x < Math.min(pixels.length, xStart + width); x++) {
            for (int y = yStart; y < Math.min(pixels[0].length, yStart + height); y++) {
                Color currentColor = new Color(pixels[x][y][0], pixels[x][y][1], pixels[x][y][2]);
                double distance = Math.sqrt(
                        Math.pow(currentColor.getRed() - avgColor.getRed(), 2) +
                        Math.pow(currentColor.getGreen() - avgColor.getGreen(), 2) +
                        Math.pow(currentColor.getBlue() - avgColor.getBlue(), 2)
                );
                if (distance <= lossThreshold * 255 / 100) {
                    count++;
                }
            }
        }
        return (double) count / (width * height) >= 0.9; //change this to say if 90% and above then good enough
    }
    
    

    private void serializeQuadNode(DataOutputStream dos, QuadNode node) throws IOException {
        dos.writeInt(node.x);
        dos.writeInt(node.y);
        dos.writeInt(node.size);

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
        int size = dis.readInt();

        boolean isLeaf = dis.readBoolean();
        if (isLeaf) {
            Color color = new Color(dis.readInt());
            return new QuadNode(x, y, size, color);
        } else {
            QuadNode node = new QuadNode(x, y, size, null);
            for (int i = 0; i < 4; i++) {
                node.children[i] = deserializeQuadNode(dis);
            }
            return node;
        }
    }

    private void reconstructImageFromQuadtree(int[][][] pixels, QuadNode node) {
        if (node.color != null) {
            for (int i = node.x; i < Math.min(pixels.length, node.x + node.size); i++) {
                for (int j = node.y; j < Math.min(pixels[0].length, node.y + node.size); j++) {
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
