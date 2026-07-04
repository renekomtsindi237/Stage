import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/constants/app_colors.dart';
import '../core/providers/locale_provider.dart';

/// Language selector dropdown widget.
/// Shows current language (flag + label + chevron) and opens a dropdown on tap.
/// [onDark] = true for navy/dark backgrounds, false for light AppBars.
class LangSwitchButton extends StatefulWidget {
  final bool onDark;
  const LangSwitchButton({super.key, this.onDark = true});

  @override
  State<LangSwitchButton> createState() => _LangSwitchButtonState();
}

class _LangSwitchButtonState extends State<LangSwitchButton> {
  static const _languages = [
    (code: 'fr', label: 'Français', flag: '🇫🇷'),
    (code: 'en', label: 'English', flag: '🇺🇸'),
  ];

  final _layerLink = LayerLink();
  OverlayEntry? _overlay;

  @override
  void dispose() {
    _closeDropdown();
    super.dispose();
  }

  void _toggleDropdown() {
    if (_overlay != null) {
      _closeDropdown();
    } else {
      _openDropdown();
    }
  }

  void _openDropdown() {
    final locale = context.read<LocaleProvider>();
    final entry = OverlayEntry(
      builder: (_) => _DropdownOverlay(
        link: _layerLink,
        locale: locale,
        languages: _languages,
        onDark: widget.onDark,
        onClose: _closeDropdown,
      ),
    );
    Overlay.of(context).insert(entry);
    setState(() => _overlay = entry);
  }

  void _closeDropdown() {
    _overlay?.remove();
    if (mounted) setState(() => _overlay = null);
  }

  @override
  Widget build(BuildContext context) {
    final locale = context.watch<LocaleProvider>();
    final current = _languages.firstWhere(
      (l) => l.code == locale.locale.languageCode,
      orElse: () => _languages[0],
    );
    final isOpen = _overlay != null;

    final textColor = widget.onDark ? Colors.white : const Color(0xFF374151);
    final borderColor = widget.onDark
        ? Colors.white.withOpacity(0.38)
        : const Color(0xFFD1D5DB);
    final bgColor = widget.onDark
        ? Colors.white.withOpacity(0.12)
        : Colors.white;

    return CompositedTransformTarget(
      link: _layerLink,
      child: GestureDetector(
        onTap: _toggleDropdown,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: borderColor),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(current.flag, style: const TextStyle(fontSize: 14)),
              const SizedBox(width: 5),
              Text(
                current.label,
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: textColor,
                ),
              ),
              const SizedBox(width: 2),
              AnimatedRotation(
                turns: isOpen ? 0.5 : 0,
                duration: const Duration(milliseconds: 180),
                child: Icon(
                  Icons.keyboard_arrow_down_rounded,
                  size: 15,
                  color: textColor.withOpacity(0.75),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DropdownOverlay extends StatelessWidget {
  final LayerLink link;
  final LocaleProvider locale;
  final List<({String code, String label, String flag})> languages;
  final bool onDark;
  final VoidCallback onClose;

  const _DropdownOverlay({
    required this.link,
    required this.locale,
    required this.languages,
    required this.onDark,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    final surfaceColor = onDark ? const Color(0xFF1A3357) : Colors.white;
    final borderColor = onDark
        ? Colors.white.withOpacity(0.18)
        : const Color(0xFFE5E7EB);
    final hoverColor = onDark
        ? Colors.white.withOpacity(0.07)
        : const Color(0xFFF3F4F6);
    final activeColor = onDark ? AppColors.gold : AppColors.teal;
    final defaultTextColor =
        onDark ? Colors.white : const Color(0xFF374151);

    return Stack(
      children: [
        // Dismiss backdrop
        Positioned.fill(
          child: GestureDetector(
            onTap: onClose,
            behavior: HitTestBehavior.translucent,
            child: const SizedBox.expand(),
          ),
        ),
        // Dropdown positioned below trigger
        CompositedTransformFollower(
          link: link,
          showWhenUnlinked: false,
          offset: const Offset(0, 40),
          child: Material(
            color: Colors.transparent,
            child: Container(
              width: 168,
              decoration: BoxDecoration(
                color: surfaceColor,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: borderColor),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.18),
                    blurRadius: 18,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: languages.map((lang) {
                    final isSelected =
                        locale.locale.languageCode == lang.code;
                    return InkWell(
                      onTap: () {
                        locale.setLocale(Locale(lang.code));
                        onClose();
                      },
                      splashColor: hoverColor,
                      highlightColor: hoverColor,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 14,
                          vertical: 11,
                        ),
                        child: Row(
                          children: [
                            Text(
                              lang.flag,
                              style: const TextStyle(fontSize: 16),
                            ),
                            const SizedBox(width: 9),
                            Expanded(
                              child: Text(
                                lang.label,
                                style: TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 13,
                                  fontWeight: isSelected
                                      ? FontWeight.w700
                                      : FontWeight.w400,
                                  color:
                                      isSelected ? activeColor : defaultTextColor,
                                ),
                              ),
                            ),
                            if (isSelected)
                              Icon(
                                Icons.check_rounded,
                                size: 16,
                                color: activeColor,
                              ),
                          ],
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
