import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/constants/app_colors.dart';
import '../../core/providers/auth_provider.dart';

class OtpScreen extends StatefulWidget {
  const OtpScreen({super.key});

  @override
  State<OtpScreen> createState() => _OtpScreenState();
}

class _OtpScreenState extends State<OtpScreen> {
  final List<TextEditingController> _controllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _focusNodes = List.generate(6, (_) => FocusNode());

  Timer? _timer;
  int _secondsLeft = 60;
  bool _canResend = false;
  bool _isSubmitting = false;
  String _otp = '';

  @override
  void initState() {
    super.initState();
    _startTimer();

    // Backspace: quand un champ est vide, retour au précédent
    for (int i = 0; i < 6; i++) {
      final index = i;
      _focusNodes[i].onKeyEvent = (node, event) {
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.backspace &&
            _controllers[index].text.isEmpty &&
            index > 0) {
          _focusNodes[index - 1].requestFocus();
          _controllers[index - 1].clear();
          _refreshOtp();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      };
    }
  }

  void _startTimer() {
    _secondsLeft = 60;
    _canResend = false;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) {
        t.cancel();
        return;
      }
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
    _timer?.cancel();
    for (final c in _controllers) c.dispose();
    for (final f in _focusNodes) f.dispose();
    super.dispose();
  }

  void _refreshOtp() {
    setState(() {
      _otp = _controllers.map((c) => c.text).join();
    });
  }

  void _onDigitChanged(int index, String value) {
    if (value.isNotEmpty) {
      if (index < 5) {
        _focusNodes[index + 1].requestFocus();
      } else {
        _focusNodes[index].unfocus();
      }
    }
    _refreshOtp();
    if (_otp.length == 6) {
      Future.microtask(_submit);
    }
  }

  Future<void> _submit() async {
    if (_otp.length != 6 || _isSubmitting) return;

    final auth = context.read<AuthProvider>();
    final email = auth.pendingOtpEmail;
    if (email == null) return;

    setState(() => _isSubmitting = true);
    auth.clearError();

    final success = await auth.verifyOtp(email, _otp);

    if (!mounted) return;
    setState(() => _isSubmitting = false);

    if (success) {
      context.go('/dashboard');
    } else {
      for (final c in _controllers) c.clear();
      setState(() => _otp = '');
      _focusNodes[0].requestFocus();
    }
  }

  Future<void> _resend() async {
    if (!_canResend) return;
    final auth = context.read<AuthProvider>();
    final email = auth.pendingOtpEmail;
    if (email == null) return;

    final ok = await auth.requestOtp(email);
    if (!mounted) return;
    if (ok) {
      _startTimer();
      for (final c in _controllers) c.clear();
      setState(() => _otp = '');
      _focusNodes[0].requestFocus();
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final email = auth.pendingOtpEmail ?? '';

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [AppColors.navyDeep, Color(0xFF0D2647), AppColors.navyDark],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 16),
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: MediaQuery.of(context).size.height -
                    MediaQuery.of(context).padding.top -
                    MediaQuery.of(context).padding.bottom -
                    32,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  const SizedBox(height: 32),

                  // Icône cadenas
                  Container(
                    width: 76,
                    height: 76,
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [AppColors.gold, AppColors.goldLight],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(22),
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.gold.withOpacity(0.4),
                          blurRadius: 24,
                          offset: const Offset(0, 8),
                        ),
                      ],
                    ),
                    child: const Icon(
                      Icons.lock_outline_rounded,
                      size: 38,
                      color: AppColors.navyDeep,
                    ),
                  ),
                  const SizedBox(height: 28),

                  // Titre
                  const Text(
                    'Vérification OTP',
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 26,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    'Saisissez le code à 6 chiffres envoyé à',
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 13,
                      color: Colors.white.withOpacity(0.65),
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    email,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: AppColors.gold,
                    ),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 40),

                  // Boîtes OTP
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(6, _buildOtpBox),
                  ),
                  const SizedBox(height: 24),

                  // Message d'erreur
                  if (auth.errorMessage != null)
                    AnimatedOpacity(
                      opacity: 1,
                      duration: const Duration(milliseconds: 300),
                      child: Container(
                        width: double.infinity,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 16, vertical: 12),
                        decoration: BoxDecoration(
                          color: AppColors.error.withOpacity(0.13),
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                              color: AppColors.error.withOpacity(0.4)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.error_outline,
                                color: AppColors.error, size: 18),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                auth.errorMessage!,
                                style: const TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 13,
                                  color: AppColors.error,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (auth.errorMessage != null) const SizedBox(height: 20),

                  // Bouton vérifier
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: ElevatedButton(
                      onPressed:
                          (_otp.length == 6 && !_isSubmitting) ? _submit : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.gold,
                        disabledBackgroundColor:
                            AppColors.gold.withOpacity(0.35),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                        elevation: 4,
                        shadowColor: AppColors.gold.withOpacity(0.4),
                      ),
                      child: _isSubmitting
                          ? const SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.5,
                                color: AppColors.navyDeep,
                              ),
                            )
                          : const Text(
                              'Vérifier le code',
                              style: TextStyle(
                                fontFamily: 'Inter',
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                                color: AppColors.navyDeep,
                              ),
                            ),
                    ),
                  ),
                  const SizedBox(height: 28),

                  // Renvoi du code
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'Code non reçu ? ',
                        style: TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 14,
                          color: Colors.white.withOpacity(0.55),
                        ),
                      ),
                      if (_canResend)
                        GestureDetector(
                          onTap: _resend,
                          child: const Text(
                            'Renvoyer',
                            style: TextStyle(
                              fontFamily: 'Inter',
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: AppColors.gold,
                              decoration: TextDecoration.underline,
                              decorationColor: AppColors.gold,
                            ),
                          ),
                        )
                      else
                        Text(
                          'Renvoyer dans ${_secondsLeft}s',
                          style: TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 14,
                            color: Colors.white.withOpacity(0.35),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 36),

                  // Retour connexion
                  TextButton.icon(
                    onPressed: () => context.go('/login'),
                    icon: Icon(Icons.arrow_back_ios_new_rounded,
                        size: 14, color: Colors.white.withOpacity(0.45)),
                    label: Text(
                      'Retour à la connexion',
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        color: Colors.white.withOpacity(0.45),
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

  Widget _buildOtpBox(int index) {
    final isFilled = _controllers[index].text.isNotEmpty;
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 5),
      width: 46,
      height: 58,
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isFilled ? AppColors.gold : AppColors.darkBorder,
          width: isFilled ? 2 : 1.2,
        ),
        boxShadow: isFilled
            ? [
                BoxShadow(
                  color: AppColors.gold.withOpacity(0.2),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                )
              ]
            : null,
      ),
      child: TextField(
        controller: _controllers[index],
        focusNode: _focusNodes[index],
        textAlign: TextAlign.center,
        keyboardType: TextInputType.number,
        maxLength: 1,
        style: const TextStyle(
          fontFamily: 'Inter',
          fontSize: 22,
          fontWeight: FontWeight.w700,
          color: Colors.white,
        ),
        decoration: const InputDecoration(
          border: InputBorder.none,
          counterText: '',
        ),
        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        onChanged: (value) => _onDigitChanged(index, value),
      ),
    );
  }
}
