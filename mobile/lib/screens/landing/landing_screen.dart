import 'dart:math';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/app_strings.dart';

class LandingScreen extends StatefulWidget {
  const LandingScreen({super.key});

  @override
  State<LandingScreen> createState() => _LandingScreenState();
}

class _LandingScreenState extends State<LandingScreen>
    with TickerProviderStateMixin {
  late AnimationController _entryCtrl;
  late AnimationController _pulseCtrl;
  late AnimationController _orbCtrl;

  // Entry animations
  late Animation<double> _logoFade;
  late Animation<double> _logoScale;
  late Animation<Offset> _titleSlide;
  late Animation<double> _titleFade;
  late Animation<double> _subtitleFade;
  late Animation<double> _card1Fade;
  late Animation<Offset> _card1Slide;
  late Animation<double> _card2Fade;
  late Animation<Offset> _card2Slide;
  late Animation<double> _card3Fade;
  late Animation<Offset> _card3Slide;
  late Animation<double> _ctaFade;

  // Pulse for CTA
  late Animation<double> _pulse;

  // Orb breathing
  late Animation<double> _orb;

  @override
  void initState() {
    super.initState();

    _entryCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1600),
    )..forward();

    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    )..repeat(reverse: true);

    _orbCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 3000),
    )..repeat(reverse: true);

    _logoFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.0, 0.35, curve: Curves.easeOut)),
    );
    _logoScale = Tween<double>(begin: 0.5, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.0, 0.40, curve: Curves.elasticOut)),
    );
    _titleSlide = Tween<Offset>(begin: const Offset(-0.3, 0), end: Offset.zero).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.2, 0.55, curve: Curves.easeOut)),
    );
    _titleFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.2, 0.55, curve: Curves.easeOut)),
    );
    _subtitleFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.35, 0.65, curve: Curves.easeOut)),
    );
    _card1Fade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.45, 0.70, curve: Curves.easeOut)),
    );
    _card1Slide = Tween<Offset>(begin: const Offset(0, 0.4), end: Offset.zero).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.45, 0.70, curve: Curves.easeOut)),
    );
    _card2Fade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.56, 0.78, curve: Curves.easeOut)),
    );
    _card2Slide = Tween<Offset>(begin: const Offset(0, 0.4), end: Offset.zero).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.56, 0.78, curve: Curves.easeOut)),
    );
    _card3Fade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.67, 0.86, curve: Curves.easeOut)),
    );
    _card3Slide = Tween<Offset>(begin: const Offset(0, 0.4), end: Offset.zero).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.67, 0.86, curve: Curves.easeOut)),
    );
    _ctaFade = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _entryCtrl, curve: const Interval(0.80, 1.0, curve: Curves.easeOut)),
    );
    _pulse = Tween<double>(begin: 0.97, end: 1.03).animate(
      CurvedAnimation(parent: _pulseCtrl, curve: Curves.easeInOut),
    );
    _orb = Tween<double>(begin: 0.8, end: 1.2).animate(
      CurvedAnimation(parent: _orbCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _entryCtrl.dispose();
    _pulseCtrl.dispose();
    _orbCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;

    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [AppColors.navyDeep, Color(0xFF0A2040), AppColors.navy],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: Stack(
          children: [
            // Animated background orbs
            AnimatedBuilder(
              animation: _orb,
              builder: (_, __) => Stack(children: [
                Positioned(
                  top: -size.width * 0.2 * _orb.value,
                  right: -size.width * 0.15,
                  child: Container(
                    width: size.width * 0.75 * _orb.value,
                    height: size.width * 0.75 * _orb.value,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.navy.withValues(alpha: 0.35),
                    ),
                  ),
                ),
                Positioned(
                  bottom: size.height * 0.28,
                  left: -size.width * 0.28,
                  child: Container(
                    width: size.width * 0.65 / _orb.value,
                    height: size.width * 0.65 / _orb.value,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.teal.withValues(alpha: 0.07),
                    ),
                  ),
                ),
                Positioned(
                  bottom: -size.width * 0.1,
                  right: -size.width * 0.1,
                  child: Container(
                    width: size.width * 0.5 * _orb.value,
                    height: size.width * 0.5 * _orb.value,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.gold.withValues(alpha: 0.04),
                    ),
                  ),
                ),
              ]),
            ),

            // Floating particles
            ..._buildParticles(size),

            // Main content
            SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const SizedBox(height: 40),

                    // Logo
                    FadeTransition(
                      opacity: _logoFade,
                      child: ScaleTransition(
                        scale: _logoScale,
                        child: _buildLogo(),
                      ),
                    ),
                    const SizedBox(height: 36),

                    // Title
                    FadeTransition(
                      opacity: _titleFade,
                      child: SlideTransition(
                        position: _titleSlide,
                        child: _buildTitle(),
                      ),
                    ),
                    const SizedBox(height: 10),

                    // Subtitle
                    FadeTransition(
                      opacity: _subtitleFade,
                      child: const Text(
                        'Votre plateforme intelligente\nde gestion microfinancière',
                        style: TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 15,
                          fontWeight: FontWeight.w400,
                          color: AppColors.textSecondary,
                          height: 1.6,
                        ),
                      ),
                    ),
                    const SizedBox(height: 40),

                    // Feature cards
                    FadeTransition(
                      opacity: _card1Fade,
                      child: SlideTransition(
                        position: _card1Slide,
                        child: _FeatureCard(
                          icon: Icons.account_balance_wallet_rounded,
                          title: AppStrings.featurePrets,
                          description: AppStrings.featurePretsDesc,
                          color: AppColors.gold,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    FadeTransition(
                      opacity: _card2Fade,
                      child: SlideTransition(
                        position: _card2Slide,
                        child: _FeatureCard(
                          icon: Icons.notifications_active_rounded,
                          title: AppStrings.featureAlertes,
                          description: AppStrings.featureAlertesDesc,
                          color: AppColors.teal,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    FadeTransition(
                      opacity: _card3Fade,
                      child: SlideTransition(
                        position: _card3Slide,
                        child: _FeatureCard(
                          icon: Icons.analytics_rounded,
                          title: AppStrings.featureKpi,
                          description: AppStrings.featureKpiDesc,
                          color: AppColors.success,
                        ),
                      ),
                    ),

                    const Spacer(),

                    // CTA
                    FadeTransition(
                      opacity: _ctaFade,
                      child: AnimatedBuilder(
                        animation: _pulse,
                        builder: (_, child) => Transform.scale(
                          scale: _pulse.value,
                          child: child,
                        ),
                        child: GestureDetector(
                          onTap: () => context.go('/login'),
                          child: Container(
                            width: double.infinity,
                            height: 56,
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(
                                colors: [AppColors.gold, AppColors.goldLight],
                                begin: Alignment.centerLeft,
                                end: Alignment.centerRight,
                              ),
                              borderRadius: BorderRadius.circular(16),
                              boxShadow: [
                                BoxShadow(
                                  color: AppColors.gold.withValues(alpha: 0.45),
                                  blurRadius: 24,
                                  spreadRadius: 2,
                                  offset: const Offset(0, 6),
                                ),
                              ],
                            ),
                            child: const Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text(
                                  'Commencer',
                                  style: TextStyle(
                                    fontFamily: 'Inter',
                                    fontSize: 16,
                                    fontWeight: FontWeight.w700,
                                    color: AppColors.navyDeep,
                                  ),
                                ),
                                SizedBox(width: 8),
                                Icon(Icons.arrow_forward_rounded, color: AppColors.navyDeep, size: 20),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 16),
                    FadeTransition(
                      opacity: _ctaFade,
                      child: Center(
                        child: Text(
                          '${AppStrings.appName} ${AppStrings.appVersion} — IMF Cameroun',
                          style: const TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 11,
                            color: AppColors.textMuted,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLogo() {
    return Container(
      width: 68,
      height: 68,
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.gold, AppColors.goldLight],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(18),
        boxShadow: [
          BoxShadow(
            color: AppColors.gold.withValues(alpha: 0.5),
            blurRadius: 28,
            spreadRadius: 2,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: const Icon(Icons.account_balance, size: 34, color: AppColors.navyDeep),
    );
  }

  Widget _buildTitle() {
    return RichText(
      text: const TextSpan(
        children: [
          TextSpan(
            text: 'Micro',
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 44,
              fontWeight: FontWeight.w800,
              color: Colors.white,
              letterSpacing: -1.5,
            ),
          ),
          TextSpan(
            text: 'Recouv',
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 44,
              fontWeight: FontWeight.w800,
              color: AppColors.gold,
              letterSpacing: -1.5,
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _buildParticles(Size size) {
    const positions = [
      [0.15, 0.18], [0.82, 0.12], [0.65, 0.42],
      [0.08, 0.58], [0.90, 0.65], [0.45, 0.82],
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
              opacity: 0.12 + v.abs() * 0.08,
              child: Container(
                width: 4,
                height: 4,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  color: AppColors.gold,
                ),
              ),
            );
          },
        ),
      );
    }).toList();
  }
}

class _FeatureCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final Color color;

  const _FeatureCard({
    required this.icon,
    required this.title,
    required this.description,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withValues(alpha: 0.1)),
      ),
      child: Row(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color, size: 22),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                    )),
                const SizedBox(height: 3),
                Text(description,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 12,
                      color: AppColors.textSecondary,
                      height: 1.4,
                    )),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
