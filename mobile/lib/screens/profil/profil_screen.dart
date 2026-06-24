import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:dio/dio.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/user.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/providers/theme_provider.dart';
import '../../core/services/api_service.dart';

class ProfilScreen extends StatefulWidget {
  const ProfilScreen({super.key});

  @override
  State<ProfilScreen> createState() => _ProfilScreenState();
}

class _ProfilScreenState extends State<ProfilScreen> {
  bool _uploading = false;

  Future<void> _pickAndUpload() async {
    final picker = ImagePicker();
    final XFile? image = await picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1024,
      maxHeight: 1024,
      imageQuality: 85,
    );
    if (image == null || !mounted) return;

    setState(() => _uploading = true);
    try {
      final apiService = context.read<ApiService>();
      final authProvider = context.read<AuthProvider>();

      final formData = FormData.fromMap({
        'file': await MultipartFile.fromFile(image.path, filename: image.name),
      });

      final user = await apiService.postMultipart<User>(
        path: '/api/v1/users/me/avatar',
        formData: formData,
        fromJson: (data) => User.fromJson(data as Map<String, dynamic>),
      );

      authProvider.updateAvatarUrl(user.avatarUrl);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Photo de profil mise à jour'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erreur upload : ${e.toString()}'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _removeAvatar() async {
    setState(() => _uploading = true);
    try {
      final apiService = context.read<ApiService>();
      final authProvider = context.read<AuthProvider>();

      await apiService.deleteAuthenticated<User>(
        path: '/api/v1/users/me/avatar',
        fromJson: (data) => User.fromJson(data as Map<String, dynamic>),
      );

      authProvider.updateAvatarUrl(null);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Photo supprimée'),
            backgroundColor: AppColors.teal,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Erreur : ${e.toString()}'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

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
    final avatarUrl = user?.avatarUrl;
    final bool hasNetworkAvatar = avatarUrl != null &&
        avatarUrl.isNotEmpty &&
        !avatarUrl.startsWith('/api/v1/public/default-avatar');

    return Container(
      padding: const EdgeInsets.all(28),
      decoration: context.cardBox,
      child: Column(
        children: [
          Stack(
            alignment: Alignment.bottomRight,
            children: [
              _buildAvatarCircle(context, avatarUrl, initials),
              GestureDetector(
                onTap: _uploading ? null : _pickAndUpload,
                child: Container(
                  width: 30,
                  height: 30,
                  decoration: BoxDecoration(
                    color: AppColors.teal,
                    shape: BoxShape.circle,
                    border: Border.all(color: context.bg, width: 2),
                  ),
                  child: _uploading
                      ? const Padding(
                          padding: EdgeInsets.all(6),
                          child: CircularProgressIndicator(
                              strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.camera_alt_rounded,
                          color: Colors.white, size: 16),
                ),
              ),
            ],
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
          if (hasNetworkAvatar) ...[
            const SizedBox(height: 12),
            TextButton.icon(
              onPressed: _uploading ? null : _removeAvatar,
              icon: const Icon(Icons.delete_outline_rounded,
                  size: 16, color: AppColors.error),
              label: const Text(
                'Supprimer la photo',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 12,
                  color: AppColors.error,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildAvatarCircle(
      BuildContext context, String? avatarUrl, String initials) {
    const size = 82.0;

    // URL réseau — afficher avec Image.network
    if (avatarUrl != null && avatarUrl.isNotEmpty) {
      final fullUrl = avatarUrl.startsWith('http')
          ? avatarUrl
          : '${ApiService.baseUrl}$avatarUrl';
      return ClipOval(
        child: Image.network(
          fullUrl,
          width: size,
          height: size,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => _defaultAvatar(size, initials),
          loadingBuilder: (ctx, child, progress) {
            if (progress == null) return child;
            return Container(
              width: size,
              height: size,
              decoration: const BoxDecoration(
                  color: AppColors.navy, shape: BoxShape.circle),
              child: const Center(
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: AppColors.gold)),
            );
          },
        ),
      );
    }

    // Pas d'avatar — asset par défaut
    return ClipOval(
      child: Image.asset(
        'assets/images/profile.png',
        width: size,
        height: size,
        fit: BoxFit.cover,
        errorBuilder: (_, __, ___) => _defaultAvatar(size, initials),
      ),
    );
  }

  Widget _defaultAvatar(double size, String initials) {
    return Container(
      width: size,
      height: size,
      decoration: const BoxDecoration(
          color: AppColors.navy, shape: BoxShape.circle),
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
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
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
