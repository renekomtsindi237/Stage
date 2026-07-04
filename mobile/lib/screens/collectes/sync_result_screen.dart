import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/collecte_locale.dart';
import '../../widgets/lang_switch_button.dart';

class SyncResultScreen extends StatelessWidget {
  final SyncResult result;

  const SyncResultScreen({super.key, required this.result});

  @override
  Widget build(BuildContext context) {
    final l10n = AppL10n.of(context);
    final timeFmt = DateFormat('HH:mm');
    final allOk = result.rejetees == 0 && result.doublons == 0;
    final primaryColor = allOk ? AppColors.success : AppColors.warning;

    return Scaffold(
      backgroundColor: context.bg,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              Align(
                alignment: Alignment.centerRight,
                child: const LangSwitchButton(onDark: false),
              ),
              const SizedBox(height: 16),
              Container(
                width: 96,
                height: 96,
                decoration: BoxDecoration(
                  color: primaryColor.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  allOk ? Icons.cloud_done_rounded : Icons.cloud_upload_rounded,
                  color: primaryColor,
                  size: 52,
                ),
              ),
              const SizedBox(height: 24),
              Text(
                l10n.syncResultTitle,
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: context.text,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 6),
              Text(
                timeFmt.format(result.syncedAt),
                style: TextStyle(fontFamily: 'Inter', fontSize: 13, color: context.textSec),
              ),
              const SizedBox(height: 32),
              Row(
                children: [
                  Expanded(child: _stat(context, '${result.totalRecu}', l10n.syncResultTotal, AppColors.navy)),
                  const SizedBox(width: 10),
                  Expanded(child: _stat(context, '${result.acceptees}', l10n.syncResultAccepted, AppColors.success)),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(child: _stat(context, '${result.doublons}', l10n.syncResultDuplicates, AppColors.warning)),
                  const SizedBox(width: 10),
                  Expanded(child: _stat(context, '${result.rejetees}', l10n.syncResultRejected, AppColors.error)),
                ],
              ),
              if (result.rejetees > 0) ...[
                const SizedBox(height: 24),
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.error.withOpacity(0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppColors.error.withOpacity(0.25)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.warning_amber_rounded, color: AppColors.error, size: 18),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          l10n.syncResultRejectedWarning(result.rejetees),
                          style: const TextStyle(fontFamily: 'Inter', fontSize: 13, color: AppColors.error),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
              const Spacer(),
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: () => context.go('/dashboard'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.navy,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  child: Text(
                    l10n.backHome,
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 16,
                      fontWeight: FontWeight.w700,
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

  Widget _stat(BuildContext context, String value, String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 12),
      decoration: context.cardBoxR(14),
      child: Column(
        children: [
          Text(
            value,
            style: TextStyle(fontFamily: 'Inter', fontSize: 28, fontWeight: FontWeight.w900, color: color),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textSec),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
