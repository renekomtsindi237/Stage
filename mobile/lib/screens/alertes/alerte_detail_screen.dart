import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/alerte.dart';
import '../../core/services/alerte_service.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/skeleton_loader.dart';
import '../../widgets/status_badge.dart';

class AlerteDetailScreen extends StatefulWidget {
  final int alerteId;

  const AlerteDetailScreen({super.key, required this.alerteId});

  @override
  State<AlerteDetailScreen> createState() => _AlerteDetailScreenState();
}

class _AlerteDetailScreenState extends State<AlerteDetailScreen> {
  Alerte? _alerte;
  bool _loading = true;
  bool _updating = false;
  String? _error;
  late final AlerteService _alerteService;

  @override
  void initState() {
    super.initState();
    _alerteService = context.read<AlerteService>();
    _loadAlerte();
  }

  Future<void> _loadAlerte() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final alerte = await _alerteService.getAlerteDetail(widget.alerteId);
      setState(() {
        _alerte = alerte;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  Future<void> _updateStatut(String statut) async {
    setState(() => _updating = true);
    try {
      final updated = await _alerteService.updateStatut(widget.alerteId, statut);
      setState(() {
        _alerte = updated;
        _updating = false;
      });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Statut mis à jour avec succès'),
          backgroundColor: AppColors.success,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          margin: const EdgeInsets.all(16),
        ),
      );
    } catch (e) {
      setState(() => _updating = false);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Erreur: ${e.toString()}'),
          backgroundColor: AppColors.error,
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          margin: const EdgeInsets.all(16),
        ),
      );
    }
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null) return '--';
    try {
      return DateFormat('dd/MM/yyyy HH:mm', 'fr').format(DateTime.parse(dateStr));
    } catch (_) {
      return dateStr;
    }
  }

  String _formatCurrency(double? value) {
    if (value == null) return '--';
    return NumberFormat.currency(
      locale: 'fr_CM',
      symbol: 'FCFA',
      decimalDigits: 0,
    ).format(value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBg,
      appBar: AppBar(
        title: const Text('Détail de l\'alerte'),
        backgroundColor: AppColors.darkBg,
        leading: IconButton(
          onPressed: () => context.go('/alertes'),
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        actions: _alerte != null
            ? [
                Padding(
                  padding: const EdgeInsets.only(right: 16),
                  child: Center(
                    child: StatusBadge(statut: _alerte!.statut),
                  ),
                ),
              ]
            : null,
      ),
      body: _loading
          ? ListView(
              padding: const EdgeInsets.all(16),
              children: const [
                SkeletonDashboardHeader(),
                SizedBox(height: 16),
                SkeletonCard(),
                SizedBox(height: 12),
                SkeletonCard(),
              ],
            )
          : _error != null
              ? AppErrorWidget(message: _error!, onRetry: _loadAlerte)
              : _buildContent(),
    );
  }

  Widget _buildContent() {
    final alerte = _alerte!;

    Color headerColor;
    IconData headerIcon;
    if (alerte.isActive) {
      headerColor = AppColors.error;
      headerIcon = Icons.warning_amber_rounded;
    } else if (alerte.isEscaladee) {
      headerColor = AppColors.warning;
      headerIcon = Icons.priority_high_rounded;
    } else {
      headerColor = AppColors.success;
      headerIcon = Icons.check_circle_rounded;
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header card
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: headerColor.withOpacity(0.1),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: headerColor.withOpacity(0.3)),
            ),
            child: Row(
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: headerColor.withOpacity(0.2),
                    shape: BoxShape.circle,
                  ),
                  child: Icon(headerIcon, color: headerColor, size: 28),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        alerte.nomClient ?? 'Prêt #${alerte.idPret}',
                        style: const TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        alerte.message ?? 'Alerte de recouvrement',
                        style: const TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 13,
                          color: AppColors.textSecondary,
                        ),
                      ),
                      if (alerte.joursRetard != null) ...[
                        const SizedBox(height: 6),
                        RetardBadge(joursRetard: alerte.joursRetard!),
                      ],
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          // Details
          _buildSection('Informations', [
            _InfoRow(label: 'Référence prêt', value: alerte.referencePret ?? '--'),
            _InfoRow(label: 'Montant dû', value: _formatCurrency(alerte.montantDu)),
            _InfoRow(label: 'Type', value: alerte.type ?? '--'),
            _InfoRow(label: 'Statut', valueWidget: StatusBadge(statut: alerte.statut)),
            _InfoRow(label: 'Créée le', value: _formatDate(alerte.dateCreation)),
            _InfoRow(label: 'Mise à jour', value: _formatDate(alerte.dateMiseAJour)),
          ]),
          // Actions
          if (!alerte.isCloturee) ...[
            const SizedBox(height: 20),
            const Text(
              'Actions',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 12),
            if (_updating)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(16),
                  child: CircularProgressIndicator(color: AppColors.gold),
                ),
              )
            else
              Column(
                children: [
                  if (alerte.isActive) ...[
                    _ActionButton(
                      label: 'Escalader',
                      icon: Icons.priority_high_rounded,
                      color: AppColors.warning,
                      onTap: () => _updateStatut('ESCALADEE'),
                    ),
                    const SizedBox(height: 8),
                    _ActionButton(
                      label: 'Marquer traitée',
                      icon: Icons.check_rounded,
                      color: AppColors.teal,
                      onTap: () => _updateStatut('TRAITEE'),
                    ),
                    const SizedBox(height: 8),
                  ],
                  _ActionButton(
                    label: 'Clôturer',
                    icon: Icons.cancel_outlined,
                    color: AppColors.textMuted,
                    onTap: () => _updateStatut('CLOTUREE'),
                  ),
                ],
              ),
          ],
          // Link to pret
          if (alerte.idPret != null) ...[
            const SizedBox(height: 20),
            GestureDetector(
              onTap: () => context.go('/prets/${alerte.idPret}'),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.darkBorder),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.account_balance_wallet_rounded,
                        color: AppColors.teal, size: 20),
                    SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        'Voir le prêt associé',
                        style: TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: AppColors.teal,
                        ),
                      ),
                    ),
                    Icon(Icons.chevron_right, color: AppColors.textMuted, size: 18),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontFamily: 'Inter',
            fontSize: 15,
            fontWeight: FontWeight.w700,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 10),
        Container(
          decoration: BoxDecoration(
            color: AppColors.darkSurface,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.darkBorder),
          ),
          child: Column(children: children),
        ),
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String? value;
  final Widget? valueWidget;

  const _InfoRow({required this.label, this.value, this.valueWidget});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(color: AppColors.darkBorder, width: 0.5),
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 13,
                color: AppColors.textSecondary,
              )),
          valueWidget ??
              Text(value ?? '--',
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  )),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;

  const _ActionButton({
    required this.label,
    required this.icon,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: color.withOpacity(0.3)),
        ),
        child: Row(
          children: [
            Icon(icon, color: color, size: 20),
            const SizedBox(width: 12),
            Text(
              label,
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: color,
              ),
            ),
            const Spacer(),
            Icon(Icons.chevron_right, color: color.withOpacity(0.5), size: 18),
          ],
        ),
      ),
    );
  }
}
