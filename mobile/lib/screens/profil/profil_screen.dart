import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:dio/dio.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/user.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/providers/locale_provider.dart';
import '../../core/providers/theme_provider.dart';
import '../../core/services/api_service.dart';
import '../../core/services/location_service.dart';

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
        final l10n = AppL10n.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.profilAvatarUpdated),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppL10n.of(context).errorWithDetail(e.toString())),
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
        final l10n = AppL10n.of(context);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.profilAvatarDeleted),
            backgroundColor: AppColors.teal,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppL10n.of(context).errorWithDetail(e.toString())),
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
    final l10n = AppL10n.of(context);
    final locale = context.watch<LocaleProvider>();
    final auth = context.watch<AuthProvider>();
    final theme = context.watch<ThemeProvider>();
    final location = context.watch<LocationService>();
    final user = auth.currentUser;

    final name = user?.fullName ?? user?.username ?? l10n.profilUserFallback;
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
          _topBar(context, l10n),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
              children: [
                _avatarCard(context, initials, name, user, l10n),
                const SizedBox(height: 24),
                _sectionLabel(context, l10n.profilSectionGps),
                const SizedBox(height: 8),
                _gpsCard(context, location, l10n),
                const SizedBox(height: 24),
                _sectionLabel(context, l10n.langSwitchTooltip.toUpperCase()),
                const SizedBox(height: 8),
                Container(
                  decoration: context.cardBoxR(12),
                  child: _languageRow(context, locale, l10n),
                ),
                const SizedBox(height: 24),
                _sectionLabel(context, l10n.profilSectionAppearance),
                const SizedBox(height: 8),
                Container(
                  decoration: context.cardBoxR(12),
                  child: _themeRow(context, theme, l10n),
                ),
                const SizedBox(height: 24),
                _sectionLabel(context, l10n.profilSectionAccount),
                const SizedBox(height: 8),
                Container(
                  decoration: context.cardBoxR(12),
                  child: _logoutRow(context, auth, l10n),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _topBar(BuildContext context, AppL10n l10n) {
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
          Text(
            l10n.profilTitle,
            style: const TextStyle(
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

  Widget _avatarCard(BuildContext context, String initials, String name, user, AppL10n l10n) {
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
              user?.displayRole ?? user?.role ?? l10n.profilRoleFallback,
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
              label: Text(
                l10n.profilAvatarDelete,
                style: const TextStyle(
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

  Widget _gpsCard(BuildContext context, LocationService location, AppL10n l10n) {
    final stateLabel = switch (location.state) {
      GpsState.active  => l10n.profilGpsActive,
      GpsState.offline => l10n.profilGpsOffline,
      GpsState.error   => l10n.profilGpsError,
      GpsState.idle    => l10n.profilGpsIdle,
    };
    final stateColor = switch (location.state) {
      GpsState.active  => AppColors.success,
      GpsState.offline => AppColors.gold,
      GpsState.error   => AppColors.error,
      GpsState.idle    => AppColors.textMuted,
    };
    final pos = location.lastPosition;
    final coordsText = pos != null
        ? '${pos.latitude.toStringAsFixed(5)}, ${pos.longitude.toStringAsFixed(5)}'
        : '—';

    final canStop = location.isTracking &&
        !location.gpsObligatoire &&
        location.state != GpsState.offline;

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: context.cardBoxR(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Statut
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(color: stateColor, shape: BoxShape.circle),
              ),
              const SizedBox(width: 8),
              Text(
                stateLabel,
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: stateColor,
                ),
              ),
              if (location.gpsObligatoire) ...[
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.error.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    l10n.profilGpsRequired,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: AppColors.error,
                    ),
                  ),
                ),
              ],
            ],
          ),

          if (location.state == GpsState.error && location.errorMessage != null) ...[
            const SizedBox(height: 6),
            Text(
              location.errorMessage!,
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 12,
                color: AppColors.error,
              ),
            ),
          ],

          // Coordonnées
          if (pos != null) ...[
            const SizedBox(height: 10),
            _infoRow(context, l10n.profilGpsCoordinates, coordsText, monospace: true),
            _infoRow(
              context,
              l10n.profilGpsAccuracy,
              pos.accuracy > 0 ? '±${pos.accuracy.toStringAsFixed(0)} m' : '—',
            ),
            if (pos.speed >= 0)
              _infoRow(
                context,
                l10n.profilGpsSpeed,
                '${(pos.speed * 3.6).toStringAsFixed(1)} km/h',
              ),
          ],

          // File d'attente
          if (location.pendingCount > 0) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: AppColors.gold.withOpacity(0.12),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  const Icon(Icons.cloud_queue_rounded, size: 14, color: AppColors.gold),
                  const SizedBox(width: 6),
                  Text(
                    l10n.profilGpsPendingPositions(location.pendingCount),
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 12,
                      color: AppColors.gold,
                    ),
                  ),
                ],
              ),
            ),
          ],

          // Bouton contrôle (désactivé si gpsObligatoire ou offline)
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: location.isTracking
                ? OutlinedButton.icon(
                    onPressed: canStop
                        ? () async {
                            final stopped = await location.stopTracking();
                            if (!stopped && context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(l10n.profilGpsSnackbarCantStop),
                                  backgroundColor: AppColors.error,
                                ),
                              );
                            }
                          }
                        : null,
                    icon: const Icon(Icons.location_off_rounded, size: 16),
                    label: Text(
                      location.gpsObligatoire
                          ? l10n.profilGpsBtnStopRequired
                          : location.state == GpsState.offline
                              ? l10n.profilGpsBtnStopOffline
                              : l10n.profilGpsBtnStop,
                    ),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: canStop ? AppColors.error : context.textSec,
                      side: BorderSide(
                        color: canStop
                            ? AppColors.error.withOpacity(0.5)
                            : context.textSec.withOpacity(0.3),
                      ),
                      textStyle: const TextStyle(fontFamily: 'Inter', fontSize: 13),
                    ),
                  )
                : ElevatedButton.icon(
                    onPressed: () => location.startTracking(),
                    icon: const Icon(Icons.my_location_rounded, size: 16),
                    label: Text(l10n.profilGpsBtnStart),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.teal,
                      foregroundColor: Colors.white,
                      textStyle: const TextStyle(fontFamily: 'Inter', fontSize: 13),
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  Widget _infoRow(BuildContext context, String label, String value,
      {bool monospace = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(
              label,
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 12,
                color: context.textSec,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: TextStyle(
                fontFamily: monospace ? 'monospace' : 'Inter',
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: context.text,
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

  Widget _languageRow(BuildContext context, LocaleProvider locale, AppL10n l10n) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          const Icon(Icons.language_rounded, color: AppColors.teal, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              locale.isFrench ? l10n.langFr : l10n.langEn,
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: context.text,
              ),
            ),
          ),
          Container(
            decoration: BoxDecoration(
              color: context.surfaceUp,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: context.border),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _langChip(context, locale, 'FR', locale.isFrench),
                _langChip(context, locale, 'EN', !locale.isFrench),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _langChip(BuildContext context, LocaleProvider locale, String code, bool active) {
    return GestureDetector(
      onTap: () => locale.setLocale(Locale(code.toLowerCase())),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 7),
        decoration: BoxDecoration(
          color: active ? AppColors.teal : Colors.transparent,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          code,
          style: TextStyle(
            fontFamily: 'Inter',
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: active ? Colors.white : context.textSec,
          ),
        ),
      ),
    );
  }

  Widget _themeRow(BuildContext context, ThemeProvider theme, AppL10n l10n) {
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
              l10n.profilThemeDarkMode,
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

  Widget _logoutRow(BuildContext context, AuthProvider auth, AppL10n l10n) {
    return InkWell(
      onTap: () async {
        final confirm = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            backgroundColor: ctx.surface,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            title: Text(
              l10n.profilDialogLogoutTitle,
              style: TextStyle(
                fontFamily: 'Inter',
                fontWeight: FontWeight.w700,
                color: ctx.text,
              ),
            ),
            content: Text(
              l10n.profilDialogLogoutContent,
              style: TextStyle(fontFamily: 'Inter', color: ctx.textSec),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(false),
                child: Text(
                  l10n.cancel,
                  style: TextStyle(fontFamily: 'Inter', color: ctx.textSec),
                ),
              ),
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(true),
                child: Text(
                  l10n.profilDialogConfirmLogout,
                  style: const TextStyle(
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
                l10n.profilLogoutLabel,
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
