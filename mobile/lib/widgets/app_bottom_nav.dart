import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import '../core/constants/app_colors.dart';
import '../core/constants/theme_helper.dart';

class AppBottomNav extends StatelessWidget {
  final int currentIndex;

  const AppBottomNav({super.key, required this.currentIndex});

  @override
  Widget build(BuildContext context) {
    final l10n = AppL10n.of(context);
    return Container(
      decoration: BoxDecoration(
        color: context.navBg,
        border: Border(
          top: BorderSide(color: context.navBorder, width: 1),
        ),
        boxShadow: context.isDark
            ? const [BoxShadow(color: Color(0x4D000000), blurRadius: 12, offset: Offset(0, -4))]
            : [BoxShadow(color: Colors.black.withOpacity(0.08), blurRadius: 12, offset: const Offset(0, -4))],
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _NavItem(
                icon: Icons.home_rounded,
                label: l10n.navHome,
                isActive: currentIndex == 0,
                onTap: () => context.go('/dashboard'),
              ),
              _NavItem(
                icon: Icons.people_rounded,
                label: l10n.navClients,
                isActive: currentIndex == 1,
                onTap: () => context.go('/clients'),
              ),
              GestureDetector(
                onTap: () => context.go('/collectes/nouvelle'),
                child: Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: AppColors.navyDeep,
                    shape: BoxShape.circle,
                    border: Border.all(color: context.navBorder, width: 1.5),
                    boxShadow: const [
                      BoxShadow(color: Color(0x4D000000), blurRadius: 8, offset: Offset(0, 2)),
                    ],
                  ),
                  child: const Icon(Icons.add, color: Colors.white, size: 26),
                ),
              ),
              _NavItem(
                icon: Icons.history_rounded,
                label: l10n.navHistorique,
                isActive: currentIndex == 3,
                onTap: () => context.go('/collectes/historique'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _NavItem({
    required this.icon,
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final inactiveColor = context.isDark ? AppColors.textMuted : const Color(0xFFB0BEC5);
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: isActive
            ? BoxDecoration(
                color: AppColors.gold.withOpacity(0.12),
                borderRadius: BorderRadius.circular(12),
              )
            : null,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: isActive ? AppColors.gold : inactiveColor,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 10,
                fontWeight: isActive ? FontWeight.w700 : FontWeight.w400,
                color: isActive ? AppColors.gold : inactiveColor,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
