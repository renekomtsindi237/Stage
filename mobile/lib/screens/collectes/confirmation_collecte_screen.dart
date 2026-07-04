import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/collecte_locale.dart';
import '../../widgets/lang_switch_button.dart';

class ConfirmationCollecteScreen extends StatelessWidget {
  final CollecteLocale collecte;

  const ConfirmationCollecteScreen({super.key, required this.collecte});

  @override
  Widget build(BuildContext context) {
    final l10n = AppL10n.of(context);
    final fmt = NumberFormat('#,###', 'fr_FR');
    final dateFmt = DateFormat('dd/MM/yyyy', 'fr_FR');
    final date = DateTime.tryParse(collecte.dateCollecte);

    return Scaffold(
      backgroundColor: context.bg,
      body: SafeArea(
        child: Column(
          children: [
            Container(
              color: AppColors.navyDark,
              padding: const EdgeInsets.fromLTRB(4, 12, 16, 16),
              child: Row(
                children: [
                  IconButton(
                    onPressed: () => context.go('/dashboard'),
                    icon: const Icon(Icons.close, color: Colors.white),
                  ),
                  Expanded(
                    child: Text(
                      l10n.confirmationTitle,
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  const LangSwitchButton(),
                ],
              ),
            ),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    const SizedBox(height: 8),
                    Container(
                      width: 88,
                      height: 88,
                      decoration: BoxDecoration(
                        color: AppColors.success.withOpacity(0.12),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.check_circle_outline_rounded,
                        color: AppColors.success,
                        size: 50,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      l10n.confirmationSuccessTitle,
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                        color: context.text,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      l10n.confirmationSuccessSubtitle,
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        color: context.textSec,
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 32),
                    Container(
                      padding: const EdgeInsets.all(20),
                      decoration: context.cardBox,
                      child: Column(
                        children: [
                          _InfoRow(
                            context,
                            l10n.confirmationAmount,
                            '${fmt.format(collecte.montantCollecte.toInt())} FCFA',
                            bold: true,
                            color: AppColors.gold,
                          ),
                          Divider(height: 24, color: context.border),
                          _InfoRow(context, l10n.confirmationClientId, collecte.clientIdExterne),
                          const SizedBox(height: 10),
                          _InfoRow(context, l10n.confirmationCanal, collecte.canalPaiement),
                          const SizedBox(height: 10),
                          _InfoRow(
                            context,
                            l10n.confirmationDate,
                            date != null ? dateFmt.format(date) : collecte.dateCollecte,
                          ),
                          if (collecte.latitude != null) ...[
                            const SizedBox(height: 10),
                            _InfoRow(
                              context,
                              l10n.confirmationGps,
                              '${collecte.latitude!.toStringAsFixed(4)}, ${collecte.longitude!.toStringAsFixed(4)}',
                            ),
                          ],
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppColors.warning.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppColors.warning.withOpacity(0.3)),
                      ),
                      child: Row(
                        children: [
                          const Icon(
                            Icons.cloud_upload_outlined,
                            color: AppColors.warning,
                            size: 20,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              l10n.confirmationPendingSync,
                              style: const TextStyle(
                                fontFamily: 'Inter',
                                fontSize: 13,
                                color: AppColors.warning,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 32),
                    SizedBox(
                      width: double.infinity,
                      height: 52,
                      child: ElevatedButton(
                        onPressed: () => context.go('/collectes/nouvelle'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.teal,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14)),
                        ),
                        child: Text(
                          l10n.confirmationNewCollecte,
                          style: const TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      height: 52,
                      child: OutlinedButton(
                        onPressed: () => context.go('/dashboard'),
                        style: OutlinedButton.styleFrom(
                          side: BorderSide(color: context.border),
                          shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(14)),
                        ),
                        child: Text(
                          l10n.backHome,
                          style: TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                            color: context.textSec,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _InfoRow(
    BuildContext context,
    String label,
    String value, {
    bool bold = false,
    Color? color,
  }) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: TextStyle(fontFamily: 'Inter', fontSize: 13, color: context.textSec),
        ),
        Text(
          value,
          style: TextStyle(
            fontFamily: 'Inter',
            fontSize: bold ? 18 : 13,
            fontWeight: bold ? FontWeight.w800 : FontWeight.w600,
            color: color ?? context.text,
          ),
        ),
      ],
    );
  }
}
