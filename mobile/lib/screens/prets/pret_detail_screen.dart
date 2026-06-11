import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/echeance.dart';
import '../../core/models/pret.dart';
import '../../core/services/pret_service.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/skeleton_loader.dart';
import '../../widgets/status_badge.dart';

class PretDetailScreen extends StatefulWidget {
  final int idPret;

  const PretDetailScreen({super.key, required this.idPret});

  @override
  State<PretDetailScreen> createState() => _PretDetailScreenState();
}

class _PretDetailScreenState extends State<PretDetailScreen> {
  Pret? _pret;
  List<Echeance> _echeances = [];
  bool _loading = true;
  String? _error;
  late final PretService _pretService;

  @override
  void initState() {
    super.initState();
    _pretService = context.read<PretService>();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        _pretService.getPretDetail(widget.idPret),
        _pretService.getEcheances(widget.idPret),
      ]);
      setState(() {
        _pret = results[0] as Pret;
        _echeances = results[1] as List<Echeance>;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  String _formatCurrency(double value) {
    return NumberFormat.currency(
      locale: 'fr_CM',
      symbol: 'FCFA',
      decimalDigits: 0,
    ).format(value);
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null) return '--';
    try {
      return DateFormat('dd/MM/yyyy', 'fr').format(DateTime.parse(dateStr));
    } catch (_) {
      return dateStr;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBg,
      appBar: AppBar(
        title: Text(_pret?.reference ?? 'DÃ©tail du prÃªt'),
        backgroundColor: AppColors.darkBg,
        leading: IconButton(
          onPressed: () => context.go('/prets'),
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        actions: _pret != null
            ? [
                Padding(
                  padding: const EdgeInsets.only(right: 16),
                  child: Center(child: StatusBadge(statut: _pret!.statut)),
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
              ? AppErrorWidget(message: _error!, onRetry: _loadData)
              : _buildContent(),
    );
  }

  Widget _buildContent() {
    final pret = _pret!;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Summary card
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [AppColors.navy, AppColors.teal],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Montant initial',
                          style: TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 12,
                            color: Colors.white70,
                          ),
                        ),
                        Text(
                          _formatCurrency(pret.montantInitial),
                          style: const TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 22,
                            fontWeight: FontWeight.w800,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                    if (pret.isEnRetard)
                      RetardBadge(joursRetard: pret.joursRetard ?? 0),
                  ],
                ),
                if (pret.montantRestant != null) ...[
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      _SummaryItem(
                        label: 'Restant',
                        value: _formatCurrency(pret.montantRestant!),
                      ),
                      if (pret.tauxInteret != null)
                        _SummaryItem(
                          label: 'Taux',
                          value: '${pret.tauxInteret!.toStringAsFixed(1)}%',
                        ),
                      if (pret.nombreEcheances != null)
                        _SummaryItem(
                          label: 'Ã‰chÃ©ances',
                          value:
                              '${pret.echeancesPayees ?? 0}/${pret.nombreEcheances}',
                        ),
                    ],
                  ),
                ],
                if (pret.nombreEcheances != null &&
                    pret.nombreEcheances! > 0) ...[
                  const SizedBox(height: 12),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: pret.progressionPaiement,
                      backgroundColor: Colors.white24,
                      valueColor:
                          const AlwaysStoppedAnimation<Color>(AppColors.gold),
                      minHeight: 6,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${(pret.progressionPaiement * 100).toStringAsFixed(0)}% remboursÃ©',
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 11,
                      color: Colors.white70,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 20),
          // Informations gÃ©nÃ©rales
          _buildSection(
            title: 'Informations gÃ©nÃ©rales',
            children: [
              _InfoRow(label: 'RÃ©fÃ©rence', value: pret.reference),
              _InfoRow(label: 'Client', value: pret.nomClient ?? '--'),
              _InfoRow(label: 'Date dÃ©but', value: _formatDate(pret.dateDebut)),
              _InfoRow(label: 'Date fin', value: _formatDate(pret.dateFin)),
              _InfoRow(
                label: 'Statut',
                value: pret.statut,
                valueWidget: StatusBadge(statut: pret.statut, small: true),
              ),
              if (pret.tauxInteret != null)
                _InfoRow(
                  label: "Taux d'intÃ©rÃªt",
                  value: '${pret.tauxInteret!.toStringAsFixed(2)}%',
                ),
            ],
          ),
          // Echeances
          if (_echeances.isNotEmpty) ...[
            const SizedBox(height: 20),
            _buildSection(
              title: 'Ã‰chÃ©ances (${_echeances.length})',
              children: [
                ..._echeances.map((e) => _EcheanceRow(
                      echeance: e,
                      formatCurrency: _formatCurrency,
                      formatDate: _formatDate,
                    )),
              ],
            ),
          ],
          // Client link
          if (pret.idClient != null) ...[
            const SizedBox(height: 20),
            GestureDetector(
              onTap: () => context.go('/clients/${pret.idClient}'),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.darkSurface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.darkBorder),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.person_rounded, color: AppColors.teal, size: 20),
                    SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        'Voir le profil client',
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

  Widget _buildSection({
    required String title,
    required List<Widget> children,
  }) {
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

class _SummaryItem extends StatelessWidget {
  final String label;
  final String value;

  const _SummaryItem({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 11,
              color: Colors.white60,
            )),
        Text(value,
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: Colors.white,
            )),
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  final Widget? valueWidget;

  const _InfoRow({required this.label, required this.value, this.valueWidget});

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
          Text(
            label,
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
          valueWidget ??
              Text(
                value,
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Colors.white,
                ),
              ),
        ],
      ),
    );
  }
}

class _EcheanceRow extends StatelessWidget {
  final Echeance echeance;
  final String Function(double) formatCurrency;
  final String Function(String?) formatDate;

  const _EcheanceRow({
    required this.echeance,
    required this.formatCurrency,
    required this.formatDate,
  });

  @override
  Widget build(BuildContext context) {
    Color statusColor;
    if (echeance.isPaid) {
      statusColor = AppColors.success;
    } else if (echeance.isOverdue) {
      statusColor = AppColors.error;
    } else {
      statusColor = AppColors.textMuted;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: AppColors.darkBorder, width: 0.5),
        ),
        color: echeance.isOverdue
            ? AppColors.error.withValues(alpha: 0.05)
            : null,
      ),
      child: Row(
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: statusColor,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Ã‰chÃ©ance #${echeance.numero ?? echeance.id}',
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  ),
                ),
                Text(
                  formatDate(echeance.dateEcheance),
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 11,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                formatCurrency(echeance.montant),
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
              StatusBadge(statut: echeance.statut, small: true),
            ],
          ),
        ],
      ),
    );
  }
}

