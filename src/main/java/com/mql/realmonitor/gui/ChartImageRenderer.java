package com.mql.realmonitor.gui;

import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.jfree.chart.JFreeChart;

/**
 * Rendert JFreeChart Objekte zu SWT Images
 */
public class ChartImageRenderer {
    
    private static final Logger LOGGER = Logger.getLogger(ChartImageRenderer.class.getName());
    
    private final Display display;
    
    // Chart-Images
    private Image mainChartImage;
    private Image drawdownChartImage;
    
    /**
     * Konstruktor
     */
    public ChartImageRenderer(Display display) {
        this.display = display;
    }
    
    /**
     * Rendert beide Charts zu Images
     * 
     * @param mainChart Der Haupt-Chart
     * @param drawdownChart Der Drawdown-Chart
     * @param chartWidth Die Breite der Charts
     * @param mainChartHeight Die Höhe des Haupt-Charts
     * @param drawdownChartHeight Die Höhe des Drawdown-Charts
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderBothChartsToImages(JFreeChart mainChart, JFreeChart drawdownChart,
                                        int chartWidth, int mainChartHeight, int drawdownChartHeight,
                                        double zoomFactor) {
        if (mainChart == null || drawdownChart == null || chartWidth <= 0) {
            return;
        }
        
        try {
            // Haupt-Chart rendern
            renderMainChartToImage(mainChart, chartWidth, mainChartHeight, zoomFactor);
            
            // Drawdown-Chart rendern
            renderDrawdownChartToImage(drawdownChart, chartWidth, drawdownChartHeight, zoomFactor);
            
            LOGGER.fine("Beide Charts gerendert: Main=" + mainChartHeight + ", Drawdown=" + drawdownChartHeight);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern der Charts", e);
        }
    }
    
    /**
     * Rendert den Haupt-Chart als Image
     */
    private void renderMainChartToImage(JFreeChart mainChart, int chartWidth, int mainChartHeight, double zoomFactor) {
        if (mainChart == null || mainChartHeight <= 0) {
            return;
        }
        
        try {
            // JFreeChart als BufferedImage rendern
            BufferedImage bufferedImage = mainChart.createBufferedImage(
                (int)(chartWidth * zoomFactor), 
                (int)(mainChartHeight * zoomFactor),
                BufferedImage.TYPE_INT_RGB,
                null
            );
            
            // BufferedImage zu SWT ImageData konvertieren
            ImageData imageData = convertBufferedImageToImageData(bufferedImage);
            
            // Alte Image-Ressource freigeben
            if (mainChartImage != null && !mainChartImage.isDisposed()) {
                mainChartImage.dispose();
            }
            
            // Neue SWT Image erstellen
            mainChartImage = new Image(display, imageData);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Haupt-Charts", e);
        }
    }
    
    /**
     * Rendert den Drawdown-Chart als Image
     */
    private void renderDrawdownChartToImage(JFreeChart drawdownChart, int chartWidth, int drawdownChartHeight, double zoomFactor) {
        if (drawdownChart == null || drawdownChartHeight <= 0) {
            return;
        }
        
        try {
            // JFreeChart als BufferedImage rendern
            BufferedImage bufferedImage = drawdownChart.createBufferedImage(
                (int)(chartWidth * zoomFactor), 
                (int)(drawdownChartHeight * zoomFactor),
                BufferedImage.TYPE_INT_RGB,
                null
            );
            
            // BufferedImage zu SWT ImageData konvertieren
            ImageData imageData = convertBufferedImageToImageData(bufferedImage);
            
            // Alte Image-Ressource freigeben
            if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
                drawdownChartImage.dispose();
            }
            
            // Neue SWT Image erstellen
            drawdownChartImage = new Image(display, imageData);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Drawdown-Charts", e);
        }
    }
    
    /**
     * Konvertiert BufferedImage zu SWT ImageData
     */
    private ImageData convertBufferedImageToImageData(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        
        // RGB-Daten extrahieren
        int[] rgbArray = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, rgbArray, 0, width);
        
        // ImageData erstellen
        ImageData imageData = new ImageData(width, height, 24, 
            new org.eclipse.swt.graphics.PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
        
        // Pixel-Daten kopieren
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = rgbArray[y * width + x];
                imageData.setPixel(x, y, rgb & 0xFFFFFF);
            }
        }
        
        return imageData;
    }
    
    /**
     * Gibt die Chart-Images frei
     */
    public void disposeImages() {
        if (mainChartImage != null && !mainChartImage.isDisposed()) {
            mainChartImage.dispose();
        }
        if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
            drawdownChartImage.dispose();
        }
    }
    
    // Getter-Methoden
    public Image getMainChartImage() {
        return mainChartImage;
    }
    
    public Image getDrawdownChartImage() {
        return drawdownChartImage;
    }
    
    public boolean hasValidImages() {
        return mainChartImage != null && !mainChartImage.isDisposed() &&
               drawdownChartImage != null && !drawdownChartImage.isDisposed();
    }
}