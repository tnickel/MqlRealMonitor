package com.mql.realmonitor.gui;

import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.jfree.chart.JFreeChart;

/**
 * ERWEITERT: Rendert jetzt beide Charts - Drawdown-Chart UND Profit-Chart
 * Verwaltet beide Chart-Images für die scrollbare Anzeige
 */
public class ChartImageRenderer {
    
    private static final Logger LOGGER = Logger.getLogger(ChartImageRenderer.class.getName());
    
    private final Display display;
    
    // Chart-Images - BEIDE CHARTS
    private Image drawdownChartImage;
    private Image profitChartImage;  // NEU
    
    /**
     * Konstruktor
     */
    public ChartImageRenderer(Display display) {
        this.display = display;
    }
    
    /**
     * ERWEITERT: Rendert beide Charts zu Images
     * 
     * @param drawdownChart Der Drawdown-Chart
     * @param profitChart Der Profit-Chart (NEU)
     * @param chartWidth Die Breite der Charts
     * @param drawdownChartHeight Die Höhe des Drawdown-Charts
     * @param profitChartHeight Die Höhe des Profit-Charts (NEU)
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderBothChartsToImages(JFreeChart drawdownChart, JFreeChart profitChart,
                                        int chartWidth, int drawdownChartHeight, int profitChartHeight,
                                        double zoomFactor) {
        LOGGER.info("=== BEIDE CHARTS RENDERN START ===");
        LOGGER.info("Charts: Drawdown=" + (drawdownChart != null) + ", Profit=" + (profitChart != null));
        LOGGER.info("Dimensionen: Breite=" + chartWidth + 
                   ", Drawdown-Höhe=" + drawdownChartHeight + 
                   ", Profit-Höhe=" + profitChartHeight + 
                   ", Zoom=" + zoomFactor);
        
        // 1. Drawdown-Chart rendern
        renderDrawdownChartToImage(drawdownChart, chartWidth, drawdownChartHeight, zoomFactor);
        
        // 2. Profit-Chart rendern
        renderProfitChartToImage(profitChart, chartWidth, profitChartHeight, zoomFactor);
        
        LOGGER.info("=== BEIDE CHARTS RENDERN ENDE ===");
    }
    
    /**
     * Rendert nur den Drawdown-Chart zu einem Image (bestehende Methode)
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
     * NEU: Rendert den Profit-Chart zu einem Image
     * 
     * @param profitChart Der Profit-Chart
     * @param chartWidth Die Breite des Charts
     * @param profitChartHeight Die Höhe des Profit-Charts
     * @param zoomFactor Der Zoom-Faktor
     */
    public void renderProfitChartToImage(JFreeChart profitChart,
                                        int chartWidth, int profitChartHeight,
                                        double zoomFactor) {
        if (profitChart == null || chartWidth <= 0 || profitChartHeight <= 0) {
            LOGGER.warning("Ungültige Parameter für Profit-Chart Rendering: " +
                          "Chart=" + (profitChart != null) + 
                          ", Width=" + chartWidth + 
                          ", Height=" + profitChartHeight);
            return;
        }
        
        try {
            LOGGER.info("Rendere Profit-Chart: " + chartWidth + "x" + profitChartHeight + 
                       " (Zoom: " + zoomFactor + ")");
            
            // JFreeChart als BufferedImage rendern
            BufferedImage bufferedImage = profitChart.createBufferedImage(
                (int)(chartWidth * zoomFactor), 
                (int)(profitChartHeight * zoomFactor),
                BufferedImage.TYPE_INT_RGB,
                null
            );
            
            // BufferedImage zu SWT ImageData konvertieren
            ImageData imageData = convertBufferedImageToImageData(bufferedImage);
            
            // Alte Image-Ressource freigeben
            if (profitChartImage != null && !profitChartImage.isDisposed()) {
                profitChartImage.dispose();
            }
            
            // Neue SWT Image erstellen
            profitChartImage = new Image(display, imageData);
            
            LOGGER.info("Profit-Chart erfolgreich gerendert");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Fehler beim Rendern des Profit-Charts", e);
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
     * Gibt beide Chart-Images frei
     */
    public void disposeImages() {
        if (drawdownChartImage != null && !drawdownChartImage.isDisposed()) {
            drawdownChartImage.dispose();
            LOGGER.fine("Drawdown-Chart Image disposed");
        }
        
        if (profitChartImage != null && !profitChartImage.isDisposed()) {
            profitChartImage.dispose();
            LOGGER.fine("Profit-Chart Image disposed");
        }
    }
    
    // Getter-Methoden - BEIDE CHARTS
    public Image getDrawdownChartImage() {
        return drawdownChartImage;
    }
    
    /**
     * NEU: Gibt das Profit-Chart Image zurück
     */
    public Image getProfitChartImage() {
        return profitChartImage;
    }
    
    public boolean hasValidDrawdownImage() {
        return drawdownChartImage != null && !drawdownChartImage.isDisposed();
    }
    
    /**
     * NEU: Prüft ob ein gültiges Profit-Chart Image vorhanden ist
     */
    public boolean hasValidProfitImage() {
        return profitChartImage != null && !profitChartImage.isDisposed();
    }
    
    /**
     * NEU: Prüft ob beide Images gültig sind
     */
    public boolean hasValidImages() {
        return hasValidDrawdownImage() && hasValidProfitImage();
    }
    
    /**
     * @deprecated Der Haupt-Chart wurde entfernt - verwende hasValidDrawdownImage() oder hasValidProfitImage()
     */
    @Deprecated
    public Image getMainChartImage() {
        LOGGER.warning("getMainChartImage() aufgerufen - Haupt-Chart wurde entfernt!");
        return null;
    }
}