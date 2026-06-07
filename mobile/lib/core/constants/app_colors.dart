import 'package:flutter/material.dart';

class AppColors {
  // Brand
  static const navy = Color(0xFF1B4F8A);
  static const navyDark = Color(0xFF0F2D52);
  static const navyDeep = Color(0xFF071A32);
  static const gold = Color(0xFFC8923A);
  static const goldLight = Color(0xFFE8B86A);
  static const teal = Color(0xFF2E87AF);

  // Dark theme surfaces
  static const darkBg = Color(0xFF071A32);
  static const darkSurface = Color(0xFF0F2D52);
  static const darkSurfaceRaised = Color(0xFF163863);
  static const darkBorder = Color(0xFF1E3F6A);

  // Light theme
  static const lightBg = Color(0xFFF7F6F2);
  static const lightSurface = Color(0xFFFFFFFF);
  static const lightBorder = Color(0xFFE0E0E0);

  // Semantic
  static const success = Color(0xFF22A06B);
  static const error = Color(0xFFE5484D);
  static const warning = Color(0xFFC8923A);
  static const info = Color(0xFF2E87AF);

  // Text
  static const textPrimary = Color(0xFFFFFFFF);
  static const textSecondary = Color(0xFFB0BEC5);
  static const textMuted = Color(0xFF607D8B);
  static const textLight = Color(0xFF1A1A1A);
  static const textLightSecondary = Color(0xFF555555);

  // Gradients
  static const goldGradient = LinearGradient(
    colors: [gold, goldLight],
    begin: Alignment.centerLeft,
    end: Alignment.centerRight,
  );

  static const navyGradient = LinearGradient(
    colors: [navyDeep, navyDark, navy],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const cardGradient = LinearGradient(
    colors: [darkSurface, darkSurfaceRaised],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}
