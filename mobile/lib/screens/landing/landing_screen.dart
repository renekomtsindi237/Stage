import 'dart:math';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import '../../core/providers/locale_provider.dart';

class LandingScreen extends StatefulWidget {
  const LandingScreen({super.key});

  @override
  State<LandingScreen> createState() => _LandingScreenState();
}

class _LandingScreenState extends State<LandingScreen>
    with TickerProviderStateMixin {
  late AnimationController _entryCtrl;
  late AnimationController _orbCtrl;

  late Animation<double> _logoFade;
  late Animation<double> _logoScale;
  late Animation<double> _badgeFade;
  late Animation<Offset> _titleSlide;
  late Animation<double> _titleFade;
  late Animation<double> _subtitleFade;
  late Animation<double> _cardFade;
  late Animation<Offset> _cardSlide;
  late Animation<double> _ctaFade;
  late Animation<double> _orb;

  static const _navy = Color(0xFF1E3A5F);
  static const _navyDeep = Color(0xFF071A32);
  static const _textMuted = Color(0xFF6B7280);
  static const _gold = Color(0xFFD4A853);
  static const _teal = Color(0xFF0D9488);
  static const _indigo = Color(0xFF4F46E5);

  @override
  void initState() {
    super.initState();

    _entryCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..forward();

    _orbCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 3600),
    )..repeat(reverse: true);

    _logoFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.0, 0.28, curve: Curves.easeOut)),
    );
    _logoScale = Tween<double>(begin: 0.75, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.0, 0.36, curve: Curves.elasticOut)),
    );
    _badgeFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.20, 0.44, curve: Curves.easeOut)),
    );
    _titleSlide = Tween<Offset>(begin: const Offset(0, 0.18), end: Offset.zero).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.30, 0.58, curve: Curves.easeOut)),
    );
    _titleFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.30, 0.58, curve: Curves.easeOut)),
    );
    _subtitleFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.46, 0.68, curve: Curves.easeOut)),
    );
    _cardFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.58, 0.82, curve: Curves.easeOut)),
    );
    _cardSlide = Tween<Offset>(begin: const Offset(0, 0.25), end: Offset.zero).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.58, 0.82, curve: Curves.easeOut)),
    );
    _ctaFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.78, 1.0, curve: Curves.easeOut)),
    );
    _orb = Tween<double>(begin: 0.88, end: 1.12).animate(
      CurvedAnimation(parent: _orbCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _entryCtrl.dispose();
    _orbCtrl.dispose();
    super.dispose();
  }

  Widget _langBtn(LocaleProvider locale, String code, bool active) {
    return GestureDetector(
      onTap: () => locale.setLocale(Locale(code.toLowerCase())),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
        decoration: BoxDecoration(
          color: active ? _navy : Colors.transparent,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          code,
          style: TextStyle(
            fontFamily: 'Inter',
            fontSize: 12,
            fontWeight: FontWeight.w700,
            color: active ? Colors.white : _textMuted,
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppL10n.of(context);
    final locale = context.watch<LocaleProvider>();
    final size = MediaQuery.of(context).size;

    return Scaffold(
      backgroundColor: Colors.white,
      body: Stack(
        children: [
          // Orbs subtils sur fond blanc
          AnimatedBuilder(
            animation: _orb,
            builder: (_, __) => Stack(children: [
              Positioned(
                top: -size.width * 0.35,
                right: -size.width * 0.20,
                child: Container(
                  width: size.width * 0.85 * _orb.value,
                  height: size.width * 0.85 * _orb.value,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _indigo.withOpacity(0.05),
                  ),
                ),
              ),
              Positioned(
                bottom: size.height * 0.20,
                left: -size.width * 0.30,
                child: Container(
                  width: size.width * 0.65 / _orb.value,
                  height: size.width * 0.65 / _orb.value,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _teal.withOpacity(0.05),
                  ),
                ),
              ),
              Positioned(
                bottom: -size.width * 0.10,
                right: -size.width * 0.10,
                child: Container(
                  width: size.width * 0.50 * _orb.value,
                  height: size.width * 0.50 * _orb.value,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _gold.withOpacity(0.06),
                  ),
                ),
              ),
            ]),
          ),

          // Particules flottantes (légères sur blanc)
          ..._buildParticles(size),

          // Contenu
          SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Language toggle — top right aligned
                  Align(
                    alignment: Alignment.centerRight,
                    child: Container(
                      margin: const EdgeInsets.only(top: 8, bottom: 4),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: const Color(0xFFE5E7EB)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.05),
                            blurRadius: 8,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          _langBtn(locale, 'FR', locale.isFrench),
                          _langBtn(locale, 'EN', !locale.isFrench),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 28),

                  // Logo
                  FadeTransition(
                    opacity: _logoFade,
                    child: ScaleTransition(
                      scale: _logoScale,
                      child: Center(child: _buildLogo()),
                    ),
                  ),
                  const SizedBox(height: 24),

                  // Badge — Flexible pour éviter l'overflow
                  FadeTransition(
                    opacity: _badgeFade,
                    child: Center(
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: _indigo.withOpacity(0.08),
                          borderRadius: BorderRadius.circular(20),
                          border: Border.all(color: _indigo.withOpacity(0.18)),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.verified_rounded, size: 13, color: _teal),
                            const SizedBox(width: 6),
                            Flexible(
                              child: Text(
                                l10n.landingBadge,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 10,
                                  fontWeight: FontWeight.w700,
                                  color: _indigo,
                                  letterSpacing: 0.4,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Titre
                  FadeTransition(
                    opacity: _titleFade,
                    child: SlideTransition(
                      position: _titleSlide,
                      child: Text(
                        l10n.landingTitle,
                        style: TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 30,
                          fontWeight: FontWeight.w800,
                          color: _navy,
                          height: 1.22,
                          letterSpacing: -0.8,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Sous-titre
                  FadeTransition(
                    opacity: _subtitleFade,
                    child: Text(
                      l10n.landingSubtitle,
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        fontWeight: FontWeight.w400,
                        color: _textMuted,
                        height: 1.65,
                      ),
                    ),
                  ),
                  const SizedBox(height: 28),

                  // Carte Accès personnel
                  FadeTransition(
                    opacity: _cardFade,
                    child: SlideTransition(
                      position: _cardSlide,
                      child: GestureDetector(
                        onTap: () => context.go('/login'),
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(22),
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                              colors: [_navyDeep, Color(0xFF0D2545)],
                              begin: Alignment.topLeft,
                              end: Alignment.bottomRight,
                            ),
                            borderRadius: BorderRadius.circular(18),
                            border: Border.all(
                              color: _gold.withOpacity(0.22),
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: _navy.withOpacity(0.18),
                                blurRadius: 28,
                                spreadRadius: 2,
                                offset: const Offset(0, 10),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                width: 46,
                                height: 46,
                                decoration: BoxDecoration(
                                  color: _gold.withOpacity(0.14),
                                  borderRadius: BorderRadius.circular(13),
                                  border: Border.all(color: _gold.withOpacity(0.28)),
                                ),
                                child: const Icon(Icons.badge_rounded, color: _gold, size: 22),
                              ),
                              const SizedBox(height: 16),
                              Text(
                                l10n.landingCardTitle,
                                style: const TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 18,
                                  fontWeight: FontWeight.w700,
                                  color: Colors.white,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                l10n.landingCardDescription,
                                style: TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 13,
                                  color: Colors.white.withOpacity(0.65),
                                  height: 1.5,
                                ),
                              ),
                              const SizedBox(height: 16),
                              _buildCheck(l10n.landingCheckEmailCode),
                              const SizedBox(height: 8),
                              _buildCheck(l10n.landingCheckNoPassword),
                              const SizedBox(height: 22),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    l10n.landingCtaButton,
                                    style: const TextStyle(
                                      fontFamily: 'Inter',
                                      fontSize: 15,
                                      fontWeight: FontWeight.w700,
                                      color: _gold,
                                    ),
                                  ),
                                  Container(
                                    width: 34,
                                    height: 34,
                                    decoration: BoxDecoration(
                                      color: _gold.withOpacity(0.14),
                                      borderRadius: BorderRadius.circular(10),
                                    ),
                                    child: const Icon(
                                      Icons.arrow_forward_rounded,
                                      color: _gold,
                                      size: 17,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),

                  // Footer
                  FadeTransition(
                    opacity: _ctaFade,
                    child: Center(
                      child: Text(
                        '${l10n.appName} ${l10n.appVersion} — ${l10n.appImfCameroun}',
                        style: const TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 11,
                          color: _textMuted,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLogo() {
    return Container(
      height: 72,
      constraints: const BoxConstraints(maxWidth: 210),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE5E7EB)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.08),
            blurRadius: 20,
            spreadRadius: 1,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Image.asset('assets/images/logo.png', fit: BoxFit.contain),
        ),
      ),
    );
  }

  Widget _buildCheck(String text) {
    return Row(
      children: [
        Container(
          width: 20,
          height: 20,
          decoration: BoxDecoration(
            color: _teal.withOpacity(0.12),
            borderRadius: BorderRadius.circular(6),
          ),
          child: const Icon(Icons.check_rounded, color: _teal, size: 12),
        ),
        const SizedBox(width: 10),
        Text(
          text,
          style: TextStyle(
            fontFamily: 'Inter',
            fontSize: 13,
            color: Colors.white.withOpacity(0.75),
          ),
        ),
      ],
    );
  }

  List<Widget> _buildParticles(Size size) {
    const positions = [
      [0.10, 0.12], [0.88, 0.08], [0.70, 0.35],
      [0.04, 0.52], [0.94, 0.60], [0.45, 0.78],
    ];
    return positions.map((pos) {
      return Positioned(
        left: size.width * pos[0],
        top: size.height * pos[1],
        child: AnimatedBuilder(
          animation: _orbCtrl,
          builder: (_, __) {
            final v = sin(_orbCtrl.value * pi * 2 + pos[0] * pi);
            return Opacity(
              opacity: 0.06 + v.abs() * 0.06,
              child: Container(
                width: 5,
                height: 5,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: _indigo,
                ),
              ),
            );
          },
        ),
      );
    }).toList();
  }
}
