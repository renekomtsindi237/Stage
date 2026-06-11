import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

class AppTheme {
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      fontFamily: GoogleFonts.inter().fontFamily,
      scaffoldBackgroundColor: AppColors.darkBg,
      colorScheme: const ColorScheme.dark(
        primary: AppColors.gold,
        onPrimary: AppColors.navyDeep,
        secondary: AppColors.teal,
        onSecondary: Colors.white,
        surface: AppColors.darkSurface,
        onSurface: AppColors.textPrimary,
        error: AppColors.error,
        onError: Colors.white,
        outline: AppColors.darkBorder,
        surfaceTint: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: AppColors.darkSurface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.darkBorder, width: 1),
        ),
        margin: EdgeInsets.zero,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.light,
        ),
        titleTextStyle: TextStyle(
          
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: AppColors.textPrimary,
        ),
        iconTheme: IconThemeData(color: AppColors.textPrimary),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.gold,
          foregroundColor: AppColors.navyDeep,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(
            
            fontSize: 15,
            fontWeight: FontWeight.w700,
          ),
          minimumSize: const Size.fromHeight(52),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: AppColors.gold,
          side: const BorderSide(color: AppColors.gold, width: 1.5),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(
            
            fontSize: 15,
            fontWeight: FontWeight.w600,
          ),
          minimumSize: const Size.fromHeight(52),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: AppColors.gold,
          textStyle: const TextStyle(
            
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.darkSurfaceRaised,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.darkBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.darkBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.gold, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.error),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.error, width: 1.5),
        ),
        labelStyle: const TextStyle(
          
          color: AppColors.textSecondary,
          fontSize: 14,
        ),
        hintStyle: const TextStyle(
          
          color: AppColors.textMuted,
          fontSize: 14,
        ),
        prefixIconColor: AppColors.textSecondary,
        suffixIconColor: AppColors.textSecondary,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: AppColors.darkSurfaceRaised,
        selectedColor: AppColors.gold.withOpacity(0.2),
        side: const BorderSide(color: AppColors.darkBorder),
        labelStyle: const TextStyle(
          
          fontSize: 12,
          fontWeight: FontWeight.w500,
          color: AppColors.textSecondary,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
      dividerTheme: const DividerThemeData(
        color: AppColors.darkBorder,
        thickness: 1,
      ),
      listTileTheme: const ListTileThemeData(
        tileColor: Colors.transparent,
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.darkSurface,
        selectedItemColor: AppColors.gold,
        unselectedItemColor: AppColors.textMuted,
        showSelectedLabels: true,
        showUnselectedLabels: true,
        type: BottomNavigationBarType.fixed,
        elevation: 0,
        selectedLabelStyle: TextStyle(
          
          fontSize: 11,
          fontWeight: FontWeight.w600,
        ),
        unselectedLabelStyle: TextStyle(
          
          fontSize: 11,
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: AppColors.darkSurfaceRaised,
        contentTextStyle: const TextStyle(
          
          color: AppColors.textPrimary,
          fontSize: 14,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        behavior: SnackBarBehavior.floating,
      ),
      progressIndicatorTheme: const ProgressIndicatorThemeData(
        color: AppColors.gold,
      ),
      textTheme: _buildTextTheme(Brightness.dark),
    );
  }

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      fontFamily: GoogleFonts.inter().fontFamily,
      scaffoldBackgroundColor: AppColors.lightBg,
      colorScheme: const ColorScheme.light(
        primary: AppColors.navy,
        onPrimary: Colors.white,
        secondary: AppColors.teal,
        onSecondary: Colors.white,
        surface: AppColors.lightSurface,
        onSurface: AppColors.textLight,
        error: AppColors.error,
        onError: Colors.white,
        outline: AppColors.lightBorder,
        surfaceTint: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: AppColors.lightSurface,
        elevation: 1,
        shadowColor: Colors.black12,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.lightBorder, width: 1),
        ),
        margin: EdgeInsets.zero,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.lightSurface,
        elevation: 0,
        scrolledUnderElevation: 1,
        systemOverlayStyle: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: Brightness.dark,
        ),
        titleTextStyle: TextStyle(
          
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: AppColors.textLight,
        ),
        iconTheme: IconThemeData(color: AppColors.textLight),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.navy,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(
            
            fontSize: 15,
            fontWeight: FontWeight.w700,
          ),
          minimumSize: const Size.fromHeight(52),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.lightSurface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.lightBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.lightBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.navy, width: 1.5),
        ),
        labelStyle: const TextStyle(
          
          color: AppColors.textLightSecondary,
          fontSize: 14,
        ),
      ),
      textTheme: _buildTextTheme(Brightness.light),
    );
  }

  static TextTheme _buildTextTheme(Brightness brightness) {
    final bool isDark = brightness == Brightness.dark;
    final Color primary = isDark ? AppColors.textPrimary : AppColors.textLight;
    final Color secondary = isDark ? AppColors.textSecondary : AppColors.textLightSecondary;
    final String interFamily = GoogleFonts.inter().fontFamily!;

    return TextTheme(
      displayLarge: TextStyle(fontFamily: interFamily, fontSize: 32, fontWeight: FontWeight.w800, color: primary),
      displayMedium: TextStyle(fontFamily: interFamily, fontSize: 28, fontWeight: FontWeight.w700, color: primary),
      displaySmall: TextStyle(fontFamily: interFamily, fontSize: 24, fontWeight: FontWeight.w700, color: primary),
      headlineLarge: TextStyle(fontFamily: interFamily, fontSize: 22, fontWeight: FontWeight.w700, color: primary),
      headlineMedium: TextStyle(fontFamily: interFamily, fontSize: 20, fontWeight: FontWeight.w600, color: primary),
      headlineSmall: TextStyle(fontFamily: interFamily, fontSize: 18, fontWeight: FontWeight.w600, color: primary),
      titleLarge: TextStyle(fontFamily: interFamily, fontSize: 16, fontWeight: FontWeight.w600, color: primary),
      titleMedium: TextStyle(fontFamily: interFamily, fontSize: 14, fontWeight: FontWeight.w600, color: primary),
      titleSmall: TextStyle(fontFamily: interFamily, fontSize: 13, fontWeight: FontWeight.w500, color: primary),
      bodyLarge: TextStyle(fontFamily: interFamily, fontSize: 16, fontWeight: FontWeight.w400, color: primary),
      bodyMedium: TextStyle(fontFamily: interFamily, fontSize: 14, fontWeight: FontWeight.w400, color: primary),
      bodySmall: TextStyle(fontFamily: interFamily, fontSize: 12, fontWeight: FontWeight.w400, color: secondary),
      labelLarge: TextStyle(fontFamily: interFamily, fontSize: 14, fontWeight: FontWeight.w600, color: primary),
      labelMedium: TextStyle(fontFamily: interFamily, fontSize: 12, fontWeight: FontWeight.w500, color: secondary),
      labelSmall: TextStyle(fontFamily: interFamily, fontSize: 11, fontWeight: FontWeight.w500, color: secondary),
    );
  }
}
