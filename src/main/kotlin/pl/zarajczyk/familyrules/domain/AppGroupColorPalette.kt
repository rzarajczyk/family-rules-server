package pl.zarajczyk.familyrules.domain

object AppGroupColorPalette {
    
    // 16 carefully chosen colors with good contrast for text
    private val colors = listOf(
        // Primary colors
        ColorInfo("#E3F2FD", "#1976D2"), // Light Blue
        ColorInfo("#F3E5F5", "#7B1FA2"), // Light Purple
        ColorInfo("#E8F5E8", "#388E3C"), // Light Green
        ColorInfo("#FFF3E0", "#F57C00"), // Light Orange
        ColorInfo("#FCE4EC", "#C2185B"), // Light Pink
        ColorInfo("#E0F2F1", "#00695C"), // Light Teal
        ColorInfo("#FFF8E1", "#F9A825"), // Light Amber
        ColorInfo("#F1F8E9", "#689F38"), // Light Lime
        
        // Secondary colors
        ColorInfo("#E1F5FE", "#0277BD"), // Light Cyan
        ColorInfo("#F9FBE7", "#827717"), // Light Yellow
        ColorInfo("#E8EAF6", "#3F51B5"), // Light Indigo
        ColorInfo("#FDF2E9", "#D84315"), // Light Deep Orange
        ColorInfo("#E0F7FA", "#00BCD4"), // Light Cyan
        ColorInfo("#F3E5F5", "#8E24AA"), // Light Purple
        ColorInfo("#E8F5E8", "#4CAF50"), // Light Green
        ColorInfo("#FFF3E0", "#FF9800")  // Light Orange
    )
    
    fun getNextColor(usedColors: Set<String>): String {
        // Find the first unused color
        for (colorInfo in colors) {
            if (!usedColors.contains(colorInfo.background)) {
                return colorInfo.background
            }
        }
        // If all colors are used, cycle back to the first one
        return colors.first().background
    }
    
    fun getDefaultColor(): String {
        return colors.first().background
    }
    
    fun getColorInfo(backgroundColor: String): ColorInfo? {
        return colors.find { it.background == backgroundColor }
    }
    
    fun getAllColors(): List<ColorInfo> = colors
}

data class ColorInfo(
    val background: String,
    val text: String
)
