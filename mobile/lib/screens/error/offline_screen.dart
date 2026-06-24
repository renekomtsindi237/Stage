import 'package:flutter/material.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/services/connectivity_service.dart';

class OfflineScreen extends StatefulWidget {
  final VoidCallback? onConnected;

  const OfflineScreen({super.key, this.onConnected});

  @override
  State<OfflineScreen> createState() => _OfflineScreenState();
}

class _OfflineScreenState extends State<OfflineScreen>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _pulse;
  bool _checking = false;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
    _pulse = Tween<double>(begin: 0.9, end: 1.1).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
    _startListening();
  }

  void _startListening() {
    ConnectivityService().onConnectivityChanged.listen((_) async {
      if (!mounted) return;
      final ok = await ConnectivityService().isConnected();
      if (ok && mounted) {
        widget.onConnected?.call();
      }
    });
  }

  Future<void> _checkNow() async {
    if (_checking) return;
    setState(() => _checking = true);
    final ok = await ConnectivityService().isConnected();
    if (!mounted) return;
    setState(() => _checking = false);
    if (ok) {
      widget.onConnected?.call();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Toujours pas de connexion'),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: context.bg,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              ScaleTransition(
                scale: _pulse,
                child: Container(
                  width: 110,
                  height: 110,
                  decoration: BoxDecoration(
                    color: context.surfaceUp,
                    shape: BoxShape.circle,
                    border: Border.all(color: context.border, width: 2),
                  ),
                  child: Icon(
                    Icons.wifi_off_rounded,
                    size: 54,
                    color: context.textSec,
                  ),
                ),
              ),
              const SizedBox(height: 36),
              Text(
                'Connexion indisponible',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: context.text,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              Text(
                'Vérifiez votre connexion Wi-Fi ou données mobiles.\nL\'application fonctionne en mode hors-ligne pour les collectes.',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 15,
                  color: context.textSec,
                  height: 1.5,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  color: AppColors.success.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppColors.success.withOpacity(0.3)),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.offline_bolt_rounded, color: AppColors.success, size: 16),
                    const SizedBox(width: 8),
                    Text(
                      'Vos collectes sont sauvegardées localement',
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 12,
                        color: AppColors.success,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 40),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton.icon(
                  onPressed: _checking ? null : _checkNow,
                  icon: _checking
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.refresh_rounded),
                  label: Text(
                    _checking ? 'Vérification...' : 'Vérifier la connexion',
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
