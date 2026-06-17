import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../core/constants/app_colors.dart';
import '../core/providers/auth_provider.dart';

class AppBottomNav extends StatelessWidget {
  final int currentIndex;

  const AppBottomNav({super.key, required this.currentIndex});

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthProvider>().currentUser;
    final role = user?.role ?? '';

    if (_isAgentRole(role)) {
      return _AgentBottomNav(currentIndex: currentIndex);
    }

    final items = _itemsForRole(role);

    return Container(
      decoration: const BoxDecoration(
        color: AppColors.darkSurface,
        border: Border(
          top: BorderSide(color: AppColors.darkBorder, width: 1),
        ),
        boxShadow: [
          BoxShadow(
            color: Color(0x4D000000),
            blurRadius: 12,
            offset: Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: items
                .asMap()
                .entries
                .map((e) => _NavItem(
                      icon: e.value.icon,
                      label: e.value.label,
                      isActive: currentIndex == e.key,
                      onTap: () => context.go(e.value.route),
                    ))
                .toList(),
          ),
        ),
      ),
    );
  }

  bool _isAgentRole(String role) {
    return role == 'AGENT_CREDIT' || role == 'AGENT_SAISIE' || role == 'AGENT';
  }

  List<_NavDef> _itemsForRole(String role) {
    switch (role) {
      case 'CHEF_AGENCE':
      case 'ANALYSTE_ENGAGEMENTS':
        return [
          _NavDef(Icons.dashboard_rounded, 'Accueil', '/dashboard'),
          _NavDef(Icons.folder_open_rounded, 'Dossiers', '/credit'),
          _NavDef(Icons.notifications_rounded, 'Alertes', '/alertes'),
          _NavDef(Icons.people_rounded, 'Clients', '/clients'),
          _NavDef(Icons.person_rounded, 'Profil', '/profil'),
        ];

      case 'CAISSIER':
        return [
          _NavDef(Icons.dashboard_rounded, 'Accueil', '/dashboard'),
          _NavDef(Icons.account_balance_wallet_rounded, 'Caisse', '/caisse'),
          _NavDef(Icons.notifications_rounded, 'Alertes', '/alertes'),
          _NavDef(Icons.person_rounded, 'Profil', '/profil'),
        ];

      case 'RESPONSABLE_RECOUVREMENT':
        return [
          _NavDef(Icons.dashboard_rounded, 'Accueil', '/dashboard'),
          _NavDef(Icons.account_balance_wallet_rounded, 'Prêts', '/prets'),
          _NavDef(Icons.notifications_rounded, 'Alertes', '/alertes'),
          _NavDef(Icons.gavel_rounded, 'Contentieux', '/recouvrement'),
          _NavDef(Icons.person_rounded, 'Profil', '/profil'),
        ];

      default:
        return [
          _NavDef(Icons.dashboard_rounded, 'Accueil', '/dashboard'),
          _NavDef(Icons.account_balance_wallet_rounded, 'Prêts', '/prets'),
          _NavDef(Icons.notifications_rounded, 'Alertes', '/alertes'),
          _NavDef(Icons.people_rounded, 'Clients', '/clients'),
          _NavDef(Icons.person_rounded, 'Profil', '/profil'),
        ];
    }
  }
}

// Agent-specific bottom nav: Accueil | Clients | [+ FAB] | Historique
class _AgentBottomNav extends StatelessWidget {
  final int currentIndex;

  const _AgentBottomNav({required this.currentIndex});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: AppColors.darkSurface,
        border: Border(
          top: BorderSide(color: AppColors.darkBorder, width: 1),
        ),
        boxShadow: [
          BoxShadow(
            color: Color(0x4D000000),
            blurRadius: 12,
            offset: Offset(0, -4),
          ),
        ],
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
                label: 'Accueil',
                isActive: currentIndex == 0,
                onTap: () => context.go('/dashboard'),
              ),
              _NavItem(
                icon: Icons.people_rounded,
                label: 'Clients',
                isActive: currentIndex == 1,
                onTap: () => context.go('/clients'),
              ),
              // Center FAB button
              GestureDetector(
                onTap: () => context.go('/collectes/nouvelle'),
                child: Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: AppColors.navyDeep,
                    shape: BoxShape.circle,
                    border: Border.all(color: AppColors.darkBorder, width: 1.5),
                    boxShadow: const [
                      BoxShadow(
                        color: Color(0x4D000000),
                        blurRadius: 8,
                        offset: Offset(0, 2),
                      ),
                    ],
                  ),
                  child: const Icon(Icons.add, color: Colors.white, size: 26),
                ),
              ),
              _NavItem(
                icon: Icons.history_rounded,
                label: 'Historique',
                isActive: currentIndex == 3,
                onTap: () => context.go('/historique'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _NavDef {
  final IconData icon;
  final String label;
  final String route;
  const _NavDef(this.icon, this.label, this.route);
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
              color: isActive ? AppColors.gold : AppColors.textMuted,
              size: 22,
            ),
            const SizedBox(height: 3),
            Text(
              label,
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 10,
                fontWeight: isActive ? FontWeight.w700 : FontWeight.w400,
                color: isActive ? AppColors.gold : AppColors.textMuted,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
