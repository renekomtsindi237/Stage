import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:microrecouv/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/providers/auth_provider.dart';
import '../../widgets/lang_switch_button.dart';

class OtpScreen extends StatefulWidget {
  const OtpScreen({super.key});

  @override
  State<OtpScreen> createState() => _OtpScreenState();
}

class _OtpScreenState extends State<OtpScreen>
    with SingleTickerProviderStateMixin {
  // ── Thème identique à LoginScreen ─────────────────────────────────────────
  static const _bg        = Color(0xFFEEF3F9);
  static const _navy      = Color(0xFF1E3A5F);
  static const _gold      = Color(0xFFC8923A);
  static const _border    = Color(0xFFD1D5DB);
  static const _fillColor = Color(0xFFF9FAFB);
  static const _hintColor = Color(0xFF9CA3AF);
  static const _mutedColor= Color(0xFF6B7280);
  static const _labelColor= Color(0xFF374151);
  static const _errorRed  = Color(0xFFDC2626);

  // ── Animation d'entrée ────────────────────────────────────────────────────
  late AnimationController _ctrl;
  late Animation<double>   _fade;
  late Animation<Offset>   _slide;

  // ── OTP ───────────────────────────────────────────────────────────────────
  final List<TextEditingController> _ctrlList =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _focusList =
      List.generate(6, (_) => FocusNode());

  Timer? _timer;
  int  _secondsLeft = 60;
  bool _canResend   = false;
  bool _submitting  = false;

  String get _otp => _ctrlList.map((c) => c.text).join();

  // ── Init ──────────────────────────────────────────────────────────────────
  @override
  void initState() {
    super.initState();

    // Animation fade+slide identique à login
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 700))
      ..forward();
    _fade  = Tween<double>(begin: 0, end: 1)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));
    _slide = Tween<Offset>(
            begin: const Offset(0, 0.06), end: Offset.zero)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));

    // Backspace : revenir au champ précédent
    for (int i = 0; i < 6; i++) {
      final idx = i;
      _focusList[i].onKeyEvent = (_, event) {
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.backspace &&
            _ctrlList[idx].text.isEmpty &&
            idx > 0) {
          _ctrlList[idx - 1].clear();
          _focusList[idx - 1].requestFocus();
          setState(() {});
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      };
    }

    _startTimer();
  }

  void _startTimer() {
    _secondsLeft = 60;
    _canResend   = false;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) { t.cancel(); return; }
      if (_secondsLeft <= 0) {
        t.cancel();
        setState(() => _canResend = true);
      } else {
        setState(() => _secondsLeft--);
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _timer?.cancel();
    for (final c in _ctrlList) c.dispose();
    for (final f in _focusList) f.dispose();
    super.dispose();
  }

  // ── Handlers ──────────────────────────────────────────────────────────────
  void _onChanged(int index, String value) {
    if (value.isNotEmpty && index < 5) {
      _focusList[index + 1].requestFocus();
    } else if (value.isNotEmpty && index == 5) {
      _focusList[index].unfocus();
    }
    setState(() {});
    if (_otp.length == 6) Future.microtask(_submit);
  }

  Future<void> _submit() async {
    if (_otp.length != 6 || _submitting) return;
    final auth  = context.read<AuthProvider>();
    final email = auth.pendingOtpEmail;
    if (email == null) return;

    setState(() => _submitting = true);
    auth.clearError();
    final ok = await auth.verifyOtp(email, _otp);
    if (!mounted) return;
    setState(() => _submitting = false);

    if (ok) {
      context.go('/dashboard');
    } else {
      for (final c in _ctrlList) c.clear();
      setState(() {});
      _focusList[0].requestFocus();
    }
  }

  Future<void> _resend() async {
    if (!_canResend) return;
    final auth  = context.read<AuthProvider>();
    final email = auth.pendingOtpEmail;
    if (email == null) return;
    final ok = await auth.requestOtp(email);
    if (!mounted) return;
    if (ok) {
      _startTimer();
      for (final c in _ctrlList) c.clear();
      setState(() {});
      _focusList[0].requestFocus();
    }
  }

  // ── Build ─────────────────────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    final l10n  = AppL10n.of(context);
    final auth  = context.watch<AuthProvider>();
    final email = auth.pendingOtpEmail ?? '';

    return Scaffold(
      backgroundColor: _bg,
      body: FadeTransition(
        opacity: _fade,
        child: SlideTransition(
          position: _slide,
          child: SafeArea(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 28),
              child: Column(
                children: [
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerRight,
                    child: const LangSwitchButton(onDark: false),
                  ),
                  const SizedBox(height: 12),

                  // ── Logo (identique à login) ──────────────────────────────
                  _buildLogo(l10n),
                  const SizedBox(height: 36),

                  // ── Card principale ───────────────────────────────────────
                  _buildCard(auth, email, l10n),
                  const SizedBox(height: 24),

                  // ── Lien retour ───────────────────────────────────────────
                  GestureDetector(
                    onTap: () => context.go('/login'),
                    child: Text(
                      l10n.otpBackToLogin,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 13,
                        color: _navy,
                        decoration: TextDecoration.underline,
                        decorationColor: _navy,
                      ),
                    ),
                  ),
                  const SizedBox(height: 32),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  // ── Widgets ───────────────────────────────────────────────────────────────

  Widget _buildLogo(AppL10n l10n) {
    return Column(
      children: [
        Container(
          width: 220,
          height: 118,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.08),
                blurRadius: 20,
                spreadRadius: 2,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
              child: Image.asset('assets/images/logo.png', fit: BoxFit.contain),
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          l10n.appImfCameroun,
          style: const TextStyle(
            fontFamily: 'Inter',
            fontSize: 13,
            fontWeight: FontWeight.w500,
            color: _mutedColor,
            letterSpacing: 0.5,
          ),
        ),
      ],
    );
  }

  Widget _buildCard(AuthProvider auth, String email, AppL10n l10n) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.07),
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Titre
          Text(
            l10n.otpTitle,
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 24,
              fontWeight: FontWeight.w800,
              color: _navy,
            ),
          ),
          const SizedBox(height: 8),
          RichText(
            text: TextSpan(
              style: const TextStyle(
                  fontFamily: 'Inter', fontSize: 13, color: _mutedColor, height: 1.5),
              children: [
                TextSpan(text: l10n.otpSubtitlePrefix),
                TextSpan(
                  text: email,
                  style: const TextStyle(
                      fontWeight: FontWeight.w600, color: _navy),
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),

          // Label
          Text(
            l10n.otpCodeLabel,
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: _labelColor,
            ),
          ),
          const SizedBox(height: 12),

          // ── Boîtes PIN ──────────────────────────────────────────────────
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: List.generate(6, _buildPinBox),
          ),
          const SizedBox(height: 20),

          // ── Message d'erreur ────────────────────────────────────────────
          if (auth.errorMessage != null) ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: _errorRed.withOpacity(0.07),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: _errorRed.withOpacity(0.35)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.error_outline, color: _errorRed, size: 16),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      auth.errorMessage!,
                      style: const TextStyle(
                        fontFamily: 'Inter', fontSize: 13, color: _errorRed),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
          ],

          // ── Bouton vérifier ─────────────────────────────────────────────
          SizedBox(
            width: double.infinity,
            height: 50,
            child: ElevatedButton(
              onPressed: (_otp.length == 6 && !_submitting) ? _submit : null,
              style: ElevatedButton.styleFrom(
                backgroundColor: _navy,
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFF9CA3AF),
                elevation: 0,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12)),
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 20, height: 20,
                      child: CircularProgressIndicator(
                          color: Colors.white, strokeWidth: 2),
                    )
                  : Text(
                      l10n.otpVerifyButton,
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
            ),
          ),
          const SizedBox(height: 20),

          // ── Renvoi du code ──────────────────────────────────────────────
          Center(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  l10n.otpResendPrompt,
                  style: const TextStyle(
                      fontFamily: 'Inter', fontSize: 13, color: _mutedColor),
                ),
                GestureDetector(
                  onTap: _canResend ? _resend : null,
                  child: Text(
                    _canResend
                        ? l10n.otpResendAction
                        : l10n.otpResendCountdown(_secondsLeft),
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: _canResend ? _gold : _hintColor,
                      decoration: _canResend ? TextDecoration.underline : null,
                      decorationColor: _gold,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPinBox(int index) {
    final isFocused = _focusList[index].hasFocus;
    final isFilled  = _ctrlList[index].text.isNotEmpty;

    Color borderColor;
    double borderWidth;
    if (isFocused) {
      borderColor = _navy;
      borderWidth = 1.8;
    } else if (isFilled) {
      borderColor = _gold;
      borderWidth = 1.5;
    } else {
      borderColor = _border;
      borderWidth = 1.0;
    }

    return SizedBox(
      width: 44,
      height: 54,
      child: TextField(
        controller: _ctrlList[index],
        focusNode: _focusList[index],
        textAlign: TextAlign.center,
        keyboardType: TextInputType.number,
        maxLength: 1,
        style: const TextStyle(
          fontFamily: 'Inter',
          fontSize: 22,
          fontWeight: FontWeight.w700,
          color: _navy,
        ),
        decoration: InputDecoration(
          counterText: '',
          filled: true,
          fillColor: _fillColor,
          contentPadding: EdgeInsets.zero,
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide(color: borderColor, width: borderWidth),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: _navy, width: 1.8),
          ),
        ),
        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        onChanged: (v) => _onChanged(index, v),
        onTap: () => setState(() {}),
      ),
    );
  }
}
