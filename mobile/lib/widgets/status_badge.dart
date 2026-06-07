import 'package:flutter/material.dart';
import '../core/constants/app_colors.dart';

class StatusBadge extends StatelessWidget {
  final String statut;
  final bool small;

  const StatusBadge({
    super.key,
    required this.statut,
    this.small = false,
  });

  @override
  Widget build(BuildContext context) {
    final config = _getConfig(statut);
    return Container(
      padding: EdgeInsets.symmetric(
        horizontal: small ? 8 : 10,
        vertical: small ? 3 : 5,
      ),
      decoration: BoxDecoration(
        color: config.bg,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        config.label,
        style: TextStyle(
          color: config.textColor,
          fontSize: small ? 10 : 12,
          fontWeight: FontWeight.w600,
          fontFamily: 'Inter',
        ),
      ),
    );
  }

  _StatusConfig _getConfig(String statut) {
    switch (statut.toUpperCase()) {
      case 'ACTIF':
      case 'ACTIVE':
        return _StatusConfig(
          label: 'Actif',
          bg: AppColors.success.withOpacity(0.15),
          textColor: AppColors.success,
        );
      case 'EN_RETARD':
        return _StatusConfig(
          label: 'En retard',
          bg: AppColors.error.withOpacity(0.15),
          textColor: AppColors.error,
        );
      case 'SOLDE':
      case 'SOLDÉ':
        return _StatusConfig(
          label: 'Soldé',
          bg: AppColors.textMuted.withOpacity(0.15),
          textColor: AppColors.textMuted,
        );
      case 'ESCALADEE':
      case 'ESCALADÉE':
        return _StatusConfig(
          label: 'Escaladée',
          bg: AppColors.warning.withOpacity(0.15),
          textColor: AppColors.warning,
        );
      case 'CLOTUREE':
      case 'CLÔTURÉE':
        return _StatusConfig(
          label: 'Clôturée',
          bg: AppColors.textMuted.withOpacity(0.15),
          textColor: AppColors.textMuted,
        );
      case 'TRAITEE':
      case 'TRAITÉE':
        return _StatusConfig(
          label: 'Traitée',
          bg: AppColors.teal.withOpacity(0.15),
          textColor: AppColors.teal,
        );
      case 'PAYEE':
      case 'PAYÉE':
        return _StatusConfig(
          label: 'Payée',
          bg: AppColors.success.withOpacity(0.15),
          textColor: AppColors.success,
        );
      case 'EN_ATTENTE':
        return _StatusConfig(
          label: 'En attente',
          bg: AppColors.info.withOpacity(0.15),
          textColor: AppColors.info,
        );
      default:
        return _StatusConfig(
          label: statut,
          bg: AppColors.navy.withOpacity(0.15),
          textColor: AppColors.navy,
        );
    }
  }
}

class _StatusConfig {
  final String label;
  final Color bg;
  final Color textColor;

  _StatusConfig({
    required this.label,
    required this.bg,
    required this.textColor,
  });
}

class RetardBadge extends StatelessWidget {
  final int joursRetard;

  const RetardBadge({super.key, required this.joursRetard});

  @override
  Widget build(BuildContext context) {
    if (joursRetard <= 0) return const SizedBox.shrink();

    final color = joursRetard > 90
        ? AppColors.error
        : joursRetard > 30
            ? AppColors.warning
            : AppColors.info;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Text(
        '$joursRetard j',
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w700,
          fontFamily: 'Inter',
        ),
      ),
    );
  }
}
