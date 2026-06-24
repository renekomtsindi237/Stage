import 'package:flutter/material.dart';
import 'app_colors.dart';

extension ThemeHelper on BuildContext {
  bool get isDark => Theme.of(this).brightness == Brightness.dark;

  Color get bg       => isDark ? AppColors.darkBg       : AppColors.lightBg;
  Color get surface  => isDark ? AppColors.darkSurface   : AppColors.lightSurface;
  Color get surfaceUp=> isDark ? AppColors.darkSurfaceRaised : const Color(0xFFF0F4F8);
  Color get border   => isDark ? AppColors.darkBorder    : AppColors.lightBorder;
  Color get text     => isDark ? AppColors.textPrimary   : AppColors.textLight;
  Color get textSec  => isDark ? AppColors.textSecondary : AppColors.textLightSecondary;
  Color get textMut  => isDark ? AppColors.textMuted     : const Color(0xFF90A4AE);
  Color get navBg    => isDark ? AppColors.darkSurface   : AppColors.lightSurface;
  Color get navBorder=> isDark ? AppColors.darkBorder    : AppColors.lightBorder;

  BoxDecoration get cardBox => BoxDecoration(
    color: surface,
    borderRadius: BorderRadius.circular(16),
    border: Border.all(color: border),
    boxShadow: isDark
        ? []
        : [BoxShadow(color: Colors.black.withOpacity(0.06), blurRadius: 8, offset: const Offset(0, 2))],
  );

  BoxDecoration cardBoxR(double r) => BoxDecoration(
    color: surface,
    borderRadius: BorderRadius.circular(r),
    border: Border.all(color: border),
    boxShadow: isDark
        ? []
        : [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 6, offset: const Offset(0, 2))],
  );
}
