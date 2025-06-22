package com.example.rastreamentoinfantil.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema de dimensões responsivas para adaptar o UI a diferentes tamanhos de tela
 */
data class ResponsiveDimensions(
    val screenWidth: Int,
    val screenHeight: Int,
    val isTablet: Boolean,
    val isLandscape: Boolean,
    
    // Espaçamentos responsivos
    val paddingSmall: Int,
    val paddingMedium: Int,
    val paddingLarge: Int,
    val paddingExtraLarge: Int,
    
    // Tamanhos de componentes
    val buttonHeight: Int,
    val textFieldHeight: Int,
    val cardElevation: Int,
    val borderRadius: Int,
    
    // Tamanhos de fonte
    val textSmall: Int,
    val textMedium: Int,
    val textLarge: Int,
    val textExtraLarge: Int,
    
    // Tamanhos de ícones
    val iconSmall: Int,
    val iconMedium: Int,
    val iconLarge: Int,
    
    // Tamanhos específicos para mapas
    val mapControlsSize: Int,
    val mapButtonSize: Int,
    
    // Tamanhos para listas
    val listItemHeight: Int,
    val listItemPadding: Int
) {
    // Extensões como propriedades da classe
    val paddingSmallDp get() = paddingSmall.dp
    val paddingMediumDp get() = paddingMedium.dp
    val paddingLargeDp get() = paddingLarge.dp
    val paddingExtraLargeDp get() = paddingExtraLarge.dp

    val buttonHeightDp get() = buttonHeight.dp
    val textFieldHeightDp get() = textFieldHeight.dp
    val cardElevationDp get() = cardElevation.dp
    val borderRadiusDp get() = borderRadius.dp

    val textSmallSp get() = textSmall.sp
    val textMediumSp get() = textMedium.sp
    val textLargeSp get() = textLarge.sp
    val textExtraLargeSp get() = textExtraLarge.sp

    val iconSmallDp get() = iconSmall.dp
    val iconMediumDp get() = iconMedium.dp
    val iconLargeDp get() = iconLarge.dp

    val mapControlsSizeDp get() = mapControlsSize.dp
    val mapButtonSizeDp get() = mapButtonSize.dp

    val listItemHeightDp get() = listItemHeight.dp
    val listItemPaddingDp get() = listItemPadding.dp
}

@Composable
fun rememberResponsiveDimensions(): ResponsiveDimensions {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isTablet = screenWidth >= 600
    val isLandscape = screenWidth > screenHeight
    
    return remember(screenWidth, screenHeight, isTablet, isLandscape) {
        when {
            isTablet -> {
                // Tablet - dimensões maiores
                ResponsiveDimensions(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    isTablet = true,
                    isLandscape = isLandscape,
                    
                    // Espaçamentos
                    paddingSmall = 8,
                    paddingMedium = 16,
                    paddingLarge = 24,
                    paddingExtraLarge = 32,
                    
                    // Componentes
                    buttonHeight = 56,
                    textFieldHeight = 64,
                    cardElevation = 8,
                    borderRadius = 12,
                    
                    // Fontes
                    textSmall = 14,
                    textMedium = 16,
                    textLarge = 20,
                    textExtraLarge = 24,
                    
                    // Ícones
                    iconSmall = 20,
                    iconMedium = 24,
                    iconLarge = 32,
                    
                    // Mapas
                    mapControlsSize = 48,
                    mapButtonSize = 56,
                    
                    // Listas
                    listItemHeight = 80,
                    listItemPadding = 16
                )
            }
            isLandscape -> {
                // Smartphone em landscape - dimensões intermediárias
                ResponsiveDimensions(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    isTablet = false,
                    isLandscape = true,
                    
                    // Espaçamentos
                    paddingSmall = 6,
                    paddingMedium = 12,
                    paddingLarge = 18,
                    paddingExtraLarge = 24,
                    
                    // Componentes
                    buttonHeight = 48,
                    textFieldHeight = 56,
                    cardElevation = 6,
                    borderRadius = 8,
                    
                    // Fontes
                    textSmall = 12,
                    textMedium = 14,
                    textLarge = 18,
                    textExtraLarge = 22,
                    
                    // Ícones
                    iconSmall = 18,
                    iconMedium = 22,
                    iconLarge = 28,
                    
                    // Mapas
                    mapControlsSize = 40,
                    mapButtonSize = 48,
                    
                    // Listas
                    listItemHeight = 64,
                    listItemPadding = 12
                )
            }
            else -> {
                // Smartphone em portrait - dimensões padrão
                ResponsiveDimensions(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    isTablet = false,
                    isLandscape = false,
                    
                    // Espaçamentos
                    paddingSmall = 8,
                    paddingMedium = 16,
                    paddingLarge = 24,
                    paddingExtraLarge = 32,
                    
                    // Componentes
                    buttonHeight = 48,
                    textFieldHeight = 56,
                    cardElevation = 4,
                    borderRadius = 8,
                    
                    // Fontes
                    textSmall = 12,
                    textMedium = 14,
                    textLarge = 16,
                    textExtraLarge = 20,
                    
                    // Ícones
                    iconSmall = 16,
                    iconMedium = 20,
                    iconLarge = 24,
                    
                    // Mapas
                    mapControlsSize = 44,
                    mapButtonSize = 48,
                    
                    // Listas
                    listItemHeight = 72,
                    listItemPadding = 16
                )
            }
        }
    }
} 