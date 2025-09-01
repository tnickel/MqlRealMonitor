package com.mql.realmonitor.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import java.util.logging.Logger;

/**
 * UIResourceManager - Verwaltet alle UI-Ressourcen wie Farben, Fonts etc.
 * Zentralisiert die Ressourcenverwaltung und sorgt für ordnungsgemäße Freigabe.
 */
public class UIResourceManager {
    
    private static final Logger LOGGER = Logger.getLogger(UIResourceManager.class.getName());
    
    private final Display display;
    
    // Standard-Farben
    private Color greenColor;
    private Color redColor;
    private Color grayColor;
    
    // Favoritenklasse-Farben (Helle Hintergrundfarben für bessere Lesbarkeit)
    private Color favoriteClass1Color;  // Grün
    private Color favoriteClass2Color;  // Gelb
    private Color favoriteClass3Color;  // Orange
    private Color favoriteClass4To10Color; // Rot (für 4-10)
    
    // Schriftarten
    private Font boldFont;
    private Font statusFont;
    
    public UIResourceManager(Display display) {
        this.display = display;
        initializeColors();
        initializeFonts();
        
        LOGGER.info("UIResourceManager initialisiert");
    }
    
    /**
     * Initialisiert alle Farben
     */
    private void initializeColors() {
        // Standard-Farben
        greenColor = new Color(display, 0, 128, 0);
        redColor = new Color(display, 200, 0, 0);
        grayColor = new Color(display, 128, 128, 128);
        
        // Favoritenklasse-Hintergrundfarben (hell und gut lesbar)
        favoriteClass1Color = new Color(display, 200, 255, 200);    // 1 = Hellgrün (sehr hell)
        favoriteClass2Color = new Color(display, 255, 255, 200);    // 2 = Hellgelb 
        favoriteClass3Color = new Color(display, 255, 220, 180);    // 3 = Hellorange
        favoriteClass4To10Color = new Color(display, 255, 200, 200); // 4-10 = Hellrot
        
        LOGGER.fine("UI-Farben initialisiert");
    }
    
    /**
     * Initialisiert alle Schriftarten
     */
    private void initializeFonts() {
        FontData[] fontData = display.getSystemFont().getFontData();
        
        // Bold Font
        fontData[0].setStyle(SWT.BOLD);
        boldFont = new Font(display, fontData[0]);
        
        // Status Font (etwas kleiner)
        fontData[0].setHeight(fontData[0].getHeight() - 1);
        fontData[0].setStyle(SWT.NORMAL);
        statusFont = new Font(display, fontData[0]);
        
        LOGGER.fine("UI-Fonts initialisiert");
    }
    
    /**
     * Gibt alle Ressourcen frei
     */
    public void dispose() {
        try {
            // Standard-Farben freigeben
            if (greenColor != null && !greenColor.isDisposed()) greenColor.dispose();
            if (redColor != null && !redColor.isDisposed()) redColor.dispose();
            if (grayColor != null && !grayColor.isDisposed()) grayColor.dispose();
            
            // Favoritenklasse-Farben freigeben
            if (favoriteClass1Color != null && !favoriteClass1Color.isDisposed()) favoriteClass1Color.dispose();
            if (favoriteClass2Color != null && !favoriteClass2Color.isDisposed()) favoriteClass2Color.dispose();
            if (favoriteClass3Color != null && !favoriteClass3Color.isDisposed()) favoriteClass3Color.dispose();
            if (favoriteClass4To10Color != null && !favoriteClass4To10Color.isDisposed()) favoriteClass4To10Color.dispose();
            
            // Fonts freigeben
            if (boldFont != null && !boldFont.isDisposed()) boldFont.dispose();
            if (statusFont != null && !statusFont.isDisposed()) statusFont.dispose();
            
            LOGGER.info("UIResourceManager Ressourcen freigegeben");
            
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Freigeben der UI-Ressourcen: " + e.getMessage());
        }
    }
    
    // ========================================================================
    // GETTER METHODS
    // ========================================================================
    
    // Standard-Farben
    public Color getGreenColor() {
        return greenColor;
    }
    
    public Color getRedColor() {
        return redColor;
    }
    
    public Color getGrayColor() {
        return grayColor;
    }
    
    // Favoritenklasse-Farben
    public Color getFavoriteClass1Color() {
        return favoriteClass1Color;
    }
    
    public Color getFavoriteClass2Color() {
        return favoriteClass2Color;
    }
    
    public Color getFavoriteClass3Color() {
        return favoriteClass3Color;
    }
    
    public Color getFavoriteClass4To10Color() {
        return favoriteClass4To10Color;
    }
    
    /**
     * Gibt die passende Favoritenklasse-Farbe basierend auf der Klasse zurück
     * 
     * @param favoriteClass Die Favoritenklasse (1-10)
     * @return Die entsprechende Farbe
     */
    public Color getFavoriteClassColor(int favoriteClass) {
        switch (favoriteClass) {
            case 1:
                return favoriteClass1Color;
            case 2:
                return favoriteClass2Color;
            case 3:
                return favoriteClass3Color;
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 10:
                return favoriteClass4To10Color;
            default:
                return null; // Keine Hintergrundfarbe für ungültige Klassen
        }
    }
    
    // Fonts
    public Font getBoldFont() {
        return boldFont;
    }
    
    public Font getStatusFont() {
        return statusFont;
    }
    
    // Display
    public Display getDisplay() {
        return display;
    }
}