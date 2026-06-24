import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/providers/theme_provider.dart';

class ProfilScreen extends StatelessWidget {
  const ProfilScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final theme = context.watch<ThemeProvider>();
    final user = auth.currentUser;

    final name = user?.fullName ?? user?.username ?? 'Utilisateur';
    final initials = name
        .trim()
        .split(' ')
        .where((s) => s.isNotEmpty)
        .take(2)
        .map((s) => s[0].toUpperCase())
        .join();

    return Scaffold(
      backgroundColor: context.bg,
      body: Column(
        children: [
          _topBar(context),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                _avatarCard(context, initials, name, user),
                const SizedBox(height: 24),
                _sectionLabel(context, 'Apparence'),
                const SizedBox(height: 8),
                Container(
                  decoration: context.cardBoxR(12),
                  child: _themeRow(context, theme),
                ),
                const SizedBox(height: 24),
                _sectionLabel(context, 'Compte'),
                const SizedBox(height: 8),
                Container(
                  decoration: context.cardBoxR(12),
                  child: _logoutRow(context, auth),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _topBar(BuildContext context) {
    return Container(
      color: AppColors.navyDark,
      padding: EdgeInsets.only(
        top: MediaQuery.of(context).padding.top + 8,
        bottom: 12,
        left: 4,
        right: 16,
      ),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
            onPressed: () => context.go('/dashboard'),
          ),
          const Text(
            'Mon Profil',
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 17,
              fontWeight: FontWeight.w700,
              color: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  Widget _avatarCard(BuildContext context, String initials, String name, user) {
    return Container(
      padding: const EdgeInsets.all(28),
      decoration: context.cardBox,
      child: Column(
        children: [
          Container(
            width: 82,
            height: 82,
            decoration: const BoxDecoration(color: AppColors.navy, shape: BoxShape.circle),
            child: Center(
              child: Text(
                initials.isNotEmpty ? initials : 'U',
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 30,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            name,
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: context.text,
            ),
            textAlign: TextAlign.center,
          ),
          if (user?.username != null) ...[
            const SizedBox(height: 4),
            Text(
              '@${user!.username}',
              style: TextStyle(fontFamily: 'Inter', fontSize: 13, color: context.textSec),
            ),
          ],
          if (user?.email != null) ...[
            const SizedBox(height: 4),
            Text(
              user!.email!,
              style: TextStyle(fontFamily: 'Inter', fontSize: 13, color: context.textSec),
            ),
          ],
          const SizedBox(height: 14),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.gold.withOpacity(0.15),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AppColors.gold.withOpacity(0.4)),
            ),
            child: Text(
              user?.displayRole ?? user?.role ?? 'Agent Terrain',
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppColors.gold,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _sectionLabel(BuildContext context, String label) {
    return Padding(
      padding: const EdgeInsets.only(left: 4),
      child: Text(
        label.toUpperCase(),
        style: TextStyle(
          fontFamily: 'Inter',
          fontSize: 11,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.8,
          color: context.textSec,
        ),
      ),
    );
  }

  Widget _themeRow(BuildContext context, ThemeProvider theme) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          Icon(
            theme.isDark ? Icons.dark_mode_rounded : Icons.light_mode_rounded,
            color: AppColors.gold,
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Mode sombre',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: context.text,
              ),
            ),
          ),
          Switch(
            value: theme.isDark,
            onChanged: (_) => theme.toggleTheme(),
            activeColor: AppColors.gold,
          ),
        ],
      ),
    );
  }

  Widget _logoutRow(BuildContext context, AuthProvider auth) {
    return InkWell(
      onTap: () async {
        final confirm = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            backgroundColor: ctx.surface,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: Text(
              'Déconnexion',
              style: TextStyle(
                fontFamily: 'Inter',
                fontWeight: FontWeight.w700,
                color: ctx.text,
              ),
            ),
            content: Text(
              'Êtes-vous sûr de vouloir vous déconnecter ?',
              style: TextStyle(fontFamily: 'Inter', color: ctx.textSec),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: Text(
                  'Annuler',
                  style: TextStyle(fontFamily: 'Inter', color: ctx.textSec),
                ),
              ),
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(true),
                child: const Text(
                  'Déconnecter',
                  style: TextStyle(
                    fontFamily: 'Inter',
                    fontWeight: FontWeight.w700,
                    color: AppColors.error,
                  ),
                ),
              ),
            ],
          ),
        );
        if (confirm == true && context.mounted) {
          await context.read<AuthProvider>().logout();
        }
      },
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        child: Row(
          children: [
            const Icon(Icons.logout_rounded, color: AppColors.error, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Se déconnecter',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: context.text,
                ),
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: context.textSec, size: 20),
          ],
        ),
      ),
    );
  }
}
