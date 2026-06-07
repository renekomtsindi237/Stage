import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/services/connectivity_service.dart';

/// Bandeau animé affiché en haut de l'écran lorsque la connexion est perdue.
///
/// Usage :
/// ```dart
/// Stack(
///   children: [
///     YourBodyWidget(),
///     const ConnectivityBanner(),
///   ],
/// )
/// ```
class ConnectivityBanner extends StatefulWidget {
  const ConnectivityBanner({super.key});

  @override
  State<ConnectivityBanner> createState() => _ConnectivityBannerState();
}

class _ConnectivityBannerState extends State<ConnectivityBanner>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Offset> _slideAnimation;
  late StreamSubscription<List<ConnectivityResult>> _sub;

  bool _offline = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );
    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, -1),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));

    WidgetsBinding.instance.addPostFrameCallback((_) {
      final svc = context.read<ConnectivityService>();
      _sub = svc.onConnectivityChanged.listen(_onConnectivityChanged);
      svc.isConnected().then((ok) {
        if (!ok && mounted) _showBanner();
      });
    });
  }

  void _onConnectivityChanged(List<ConnectivityResult> results) {
    final connected = results.any((r) => r != ConnectivityResult.none);
    if (!connected && !_offline) {
      _showBanner();
    } else if (connected && _offline) {
      _hideBanner();
    }
  }

  void _showBanner() {
    if (!mounted) return;
    setState(() => _offline = true);
    _controller.forward();
  }

  void _hideBanner() {
    if (!mounted) return;
    _controller.reverse().then((_) {
      if (mounted) setState(() => _offline = false);
    });
  }

  @override
  void dispose() {
    _sub.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_offline) return const SizedBox.shrink();

    return Positioned(
      top: 0,
      left: 0,
      right: 0,
      child: SlideTransition(
        position: _slideAnimation,
        child: SafeArea(
          bottom: false,
          child: Material(
            elevation: 4,
            color: const Color(0xFFB71C1C),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
              child: Row(
                children: [
                  const Icon(Icons.wifi_off_rounded,
                      color: Colors.white, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _label(context),
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 13,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  String _label(BuildContext context) {
    return 'Connexion indisponible';
  }
}
