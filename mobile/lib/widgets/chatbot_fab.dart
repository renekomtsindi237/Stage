import 'dart:async';
import 'package:flutter/material.dart';
import '../core/constants/app_colors.dart';
import '../core/services/api_service.dart';

class ChatMessage {
  final String role;
  final String content;
  final DateTime ts;
  ChatMessage({required this.role, required this.content, required this.ts});
}

class ChatbotFab extends StatefulWidget {
  final ApiService api;
  const ChatbotFab({super.key, required this.api});

  @override
  State<ChatbotFab> createState() => _ChatbotFabState();
}

class _ChatbotFabState extends State<ChatbotFab>
    with SingleTickerProviderStateMixin {
  bool _open = false;
  bool _loading = false;
  final List<ChatMessage> _messages = [];
  final _inputCtrl = TextEditingController();
  final _scrollCtrl = ScrollController();
  late final AnimationController _anim;
  late final Animation<double> _scale;
  late final Animation<double> _fade;

  static const _systemColor = AppColors.navyDark;

  @override
  void initState() {
    super.initState();
    _anim = AnimationController(vsync: this, duration: const Duration(milliseconds: 220));
    _scale = CurvedAnimation(parent: _anim, curve: Curves.easeOutBack);
    _fade  = CurvedAnimation(parent: _anim, curve: Curves.easeOut);
  }

  @override
  void dispose() {
    _anim.dispose();
    _inputCtrl.dispose();
    _scrollCtrl.dispose();
    super.dispose();
  }

  void _toggle() {
    setState(() => _open = !_open);
    if (_open) {
      _anim.forward();
      if (_messages.isEmpty) {
        _messages.add(ChatMessage(
          role: 'assistant',
          content: 'Bonjour ! Je suis l\'assistant IA de MicroRecouv. Je peux vous aider avec vos KPIs, indicateurs COBAC, scores MCRS et questions de microfinance. Comment puis-je vous aider ?',
          ts: DateTime.now(),
        ));
      }
    } else {
      _anim.reverse();
    }
  }

  Future<void> _send() async {
    final text = _inputCtrl.text.trim();
    if (text.isEmpty || _loading) return;
    _inputCtrl.clear();

    setState(() {
      _messages.add(ChatMessage(role: 'user', content: text, ts: DateTime.now()));
      _loading = true;
    });
    _scrollToBottom();

    try {
      final body = {
        'messages': _messages
            .where((m) => m.role != 'assistant' || _messages.indexOf(m) > 0)
            .map((m) => {'role': m.role, 'content': m.content})
            .toList(),
      };

      final r = await widget.api.post<Map<String, dynamic>>(
        '/api/v1/ai/chat',
        data: body,
        fromJson: (j) => j as Map<String, dynamic>,
      );

      final reply = (r as Map<String, dynamic>?)?['data']?.toString() ??
          'Désolé, réponse indisponible.';

      setState(() {
        _messages.add(ChatMessage(role: 'assistant', content: reply, ts: DateTime.now()));
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _messages.add(ChatMessage(
          role: 'assistant',
          content: 'Erreur de connexion. Vérifiez votre réseau et réessayez.',
          ts: DateTime.now(),
        ));
        _loading = false;
      });
    }
    _scrollToBottom();
  }

  void _scrollToBottom() {
    Timer(const Duration(milliseconds: 100), () {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      alignment: Alignment.bottomRight,
      children: [
        // ── Chat panel ──────────────────────────────────
        if (_open)
          Positioned(
            bottom: 80,
            right: 0,
            child: FadeTransition(
              opacity: _fade,
              child: ScaleTransition(
                scale: _scale,
                alignment: Alignment.bottomRight,
                child: _buildPanel(),
              ),
            ),
          ),

        // ── FAB ─────────────────────────────────────────
        GestureDetector(
          onTap: _toggle,
          child: Container(
            width: 54,
            height: 54,
            decoration: BoxDecoration(
              color: AppColors.teal,
              shape: BoxShape.circle,
              boxShadow: [BoxShadow(color: AppColors.teal.withOpacity(0.45), blurRadius: 14, offset: const Offset(0, 4))],
            ),
            child: Icon(
              _open ? Icons.close_rounded : Icons.smart_toy_rounded,
              color: Colors.white,
              size: 26,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildPanel() {
    return Container(
      width: 320,
      height: 460,
      decoration: BoxDecoration(
        color: _systemColor,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withOpacity(0.09)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.5), blurRadius: 30, offset: const Offset(0, 8))],
      ),
      child: Column(
        children: [
          _buildHeader(),
          Expanded(child: _buildMessages()),
          if (_loading) _buildTyping(),
          _buildInput(),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: const BoxDecoration(
        gradient: LinearGradient(colors: [Color(0xFF1d4ed8), Color(0xFF3b82f6)]),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Row(
        children: [
          const Text('🤖', style: TextStyle(fontSize: 20)),
          const SizedBox(width: 10),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Assistant MicroRecouv', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13, fontFamily: 'Inter')),
                Text('IA • COBAC/BEAC • MCRS', style: TextStyle(color: Color(0xAAFFFFFF), fontSize: 10, fontFamily: 'Inter')),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.delete_outline_rounded, color: Colors.white, size: 18),
            onPressed: () => setState(() { _messages.clear(); _toggle(); _toggle(); }),
            tooltip: 'Effacer',
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  Widget _buildMessages() {
    return ListView.builder(
      controller: _scrollCtrl,
      padding: const EdgeInsets.all(12),
      itemCount: _messages.length,
      itemBuilder: (_, i) {
        final m = _messages[i];
        final isUser = m.role == 'user';
        return Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Row(
            mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              if (!isUser) ...[
                const Text('🤖', style: TextStyle(fontSize: 16)),
                const SizedBox(width: 6),
              ],
              Flexible(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                  decoration: BoxDecoration(
                    color: isUser
                        ? AppColors.teal.withOpacity(0.3)
                        : Colors.white.withOpacity(0.06),
                    borderRadius: BorderRadius.only(
                      topLeft:     const Radius.circular(12),
                      topRight:    const Radius.circular(12),
                      bottomLeft:  Radius.circular(isUser ? 12 : 2),
                      bottomRight: Radius.circular(isUser ? 2  : 12),
                    ),
                  ),
                  child: Text(
                    m.content,
                    style: TextStyle(
                      color: isUser ? const Color(0xFFBAE6FD) : const Color(0xFFE2E8F0),
                      fontSize: 12.5,
                      height: 1.5,
                      fontFamily: 'Inter',
                    ),
                  ),
                ),
              ),
              if (isUser) ...[
                const SizedBox(width: 6),
                const Text('👤', style: TextStyle(fontSize: 14)),
              ],
            ],
          ),
        );
      },
    );
  }

  Widget _buildTyping() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      child: Row(
        children: [
          const Text('🤖', style: TextStyle(fontSize: 14)),
          const SizedBox(width: 8),
          _TypingDots(),
        ],
      ),
    );
  }

  Widget _buildInput() {
    return Container(
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 10),
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: Colors.white.withOpacity(0.07))),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _inputCtrl,
              maxLines: 3,
              minLines: 1,
              onSubmitted: (_) => _send(),
              style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 13, fontFamily: 'Inter'),
              decoration: InputDecoration(
                hintText: 'Posez votre question…',
                hintStyle: const TextStyle(color: Color(0xFF64748B), fontSize: 12),
                filled: true,
                fillColor: Colors.white.withOpacity(0.05),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide(color: Colors.white.withOpacity(0.1)),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide(color: Colors.white.withOpacity(0.1)),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.teal),
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                isDense: true,
              ),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: _send,
            child: Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: _loading ? Colors.grey : AppColors.teal,
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.send_rounded, color: Colors.white, size: 18),
            ),
          ),
        ],
      ),
    );
  }
}

class _TypingDots extends StatefulWidget {
  @override
  State<_TypingDots> createState() => _TypingDotsState();
}

class _TypingDotsState extends State<_TypingDots> with SingleTickerProviderStateMixin {
  late AnimationController _c;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 900))..repeat();
  }

  @override
  void dispose() { _c.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _c,
      builder: (_, __) => Row(
        children: List.generate(3, (i) {
          final offset = (i / 3.0);
          final v = (((_c.value - offset) % 1.0 + 1.0) % 1.0);
          final bounce = v < 0.5 ? v * 2 : (1 - v) * 2;
          return Container(
            margin: const EdgeInsets.symmetric(horizontal: 2),
            width: 6,
            height: 6 + bounce * 4,
            decoration: BoxDecoration(
              color: const Color(0xFF64748B),
              borderRadius: BorderRadius.circular(3),
            ),
          );
        }),
      ),
    );
  }
}
