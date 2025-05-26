package com.mql.realmonitor.gui;

import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.jfree.chart.JFreeChart;

/**
 * VEREINFACHT: Rendert nur den Drawdown-Chart zu SWT Images
 * Haupt-Chart wurde entfernt - nur noch Drawdown-Chart wird gerendert
 */
public class ChartImageRenderer {
    
    private static final Logger LOGGER = Logger.getLogger(ChartImageRenderer.class.getName());
    
    private final Display display;
    
    // Chart-Image - NUR DRAWDOWN
    private Image drawdownChartImage;
    
    /**
     * Konstruktor
     */
    public ChartImageRenderer(Display display) {
        this.display = display;
    }
    
    /**
     * VEREINFACHT: Rendert nur den Drawdown-Chart zu einem Image
     * 
     * @param drawdownChart Der Drawdown-Chart
     * @param chartWidth Die Breite des Charts
     * @param drawdownChartHeight Die Höhe des Drawdown-Charts
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderDrawdownChartToImage(JFreeChart drawdownChart,
                                          int chartWidth, int drawdownChartHeight,
                                          double zoomFactor) {
        if (drawdownChart == null || chartWidth <= 0 || drawdownChartHeight <= 0) {
            LOGGER.warning("Ungültige Parameter für Drawdown-Chart Rendering: " +
                          "Chart=" + (drawdownChart != null) + 
                          ", Width=" + chartWidth + 
                          ", Height=" + drawdownChartHeight);
            return;
        }
        
        try {
            LOGGER.info("Rendere Drawdown-Chart: " + chartWidth + "x" + drawdownChartHeight + 
                       " (Zoom: " + zoomFactor + ")");
            
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
            
            LOGGER.info("Drawdown-Chart erfolgreich gerendert");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Drawdown-Charts", e);
        }
    }
    
    /**
     * @deprecated Der Haupt-Chart wurde entfernt - verwende renderDrawdownChartToImage()
     */
    @Deprecated
    public void renderBothChartsToImages(JFreeChart mainChart, JFreeChart drawdownChart,
                                        int chartWidth, int mainChartHeight, int drawdownChartHeight,
                                        double zoomFactor) {
        LOGGER.warning("renderBothChartsToImages() aufgerufen - Haupt-Chart wurde entfernt!");
        LOGGER.info("Fallback: Rendere nur Drawdown-Chart");
        
        // Fallback: Verwende die neue Methode
        renderDrawdownChartToImage(drawdownChart, chartWidth, drawdownChartHeight, zoomFactor);
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
        if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
            drawdownChartImage.dispose();
        }
    }
    
    // Getter-Methoden - NUR DRAWDOWN
    public Image getDrawdownChartImage() {
        return drawdownChartImage;
    }
    
    /**
     * @deprecated Der Haupt-Chart wurde entfernt - verwende getDrawdownChartImage()
     */
    @Deprecated
    public Image getMainChartImage() {
        LOGGER.warning("getMainChartImage() aufgerufen - Haupt-Chart wurde entfernt!");
        return null;
    }
    
    public boolean hasValidDrawdownImage() {
        return drawdownChartImage != null && !drawdownChartImage.isDisposed();
    }
    
    /**
     * @deprecated Verwende hasValidDrawdownImage()
     */
    @Deprecated
    public boolean hasValidImages() {
        return hasValidDrawdownImage();
    }
}