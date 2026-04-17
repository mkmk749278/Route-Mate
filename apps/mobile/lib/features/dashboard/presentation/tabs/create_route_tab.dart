import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class CreateRouteTab extends StatefulWidget {
  const CreateRouteTab({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  State<CreateRouteTab> createState() => _CreateRouteTabState();
}

class _CreateRouteTabState extends State<CreateRouteTab> {
  final _formKey = GlobalKey<FormState>();
  final _originController = TextEditingController();
  final _destinationController = TextEditingController();
  final _seatCountController = TextEditingController();
  final _notesController = TextEditingController();
  DateTime? _travelDate;
  TimeOfDay? _departureTime;
  bool _loadingMyRoutes = false;
  String? _myRoutesLoadError;
  bool _isSubmittingRoute = false;

  @override
  void initState() {
    super.initState();
    _loadMyRoutes();
  }

  @override
  void dispose() {
    _originController.dispose();
    _destinationController.dispose();
    _seatCountController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _loadMyRoutes() async {
    setState(() {
      _loadingMyRoutes = true;
      _myRoutesLoadError = null;
    });

    try {
      await widget.controller.fetchMyRoutes();
    } catch (error) {
      if (mounted) {
        setState(() {
          _myRoutesLoadError = 'Failed to load routes. Please try again.';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _loadingMyRoutes = false;
        });
      }
    }
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final result = await showDatePicker(
      context: context,
      firstDate: DateTime(now.year, now.month, now.day),
      lastDate: DateTime(now.year + 1),
      initialDate: _travelDate ?? now,
    );

    if (result == null) {
      return;
    }

    setState(() {
      _travelDate = result;
    });
  }

  Future<void> _pickTime() async {
    final result = await showTimePicker(
      context: context,
      initialTime: _departureTime ?? const TimeOfDay(hour: 9, minute: 0),
    );

    if (result == null) {
      return;
    }

    setState(() {
      _departureTime = result;
    });
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    if (_travelDate == null || _departureTime == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Select travel date and departure time')),
      );
      return;
    }

    final seatCountText = _seatCountController.text.trim();
    final payload = <String, dynamic>{
      'origin': _originController.text.trim(),
      'destination': _destinationController.text.trim(),
      'travelDate': DateTime.utc(
        _travelDate!.year,
        _travelDate!.month,
        _travelDate!.day,
      ).toIso8601String(),
      'preferredDepartureTime':
          '${_departureTime!.hour.toString().padLeft(2, '0')}:${_departureTime!.minute.toString().padLeft(2, '0')}',
      'notes': _notesController.text.trim(),
    };

    if (seatCountText.isNotEmpty) {
      payload['seatCount'] = int.parse(seatCountText);
    }

    try {
      setState(() {
        _isSubmittingRoute = true;
      });
      await widget.controller.createRoute(payload);
      if (!mounted) {
        return;
      }

      _originController.clear();
      _destinationController.clear();
      _seatCountController.clear();
      _notesController.clear();
      setState(() {
        _travelDate = null;
        _departureTime = null;
      });

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Route posted')));
    } catch (error) {
      if (!mounted) {
        return;
      }

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) {
        setState(() {
          _isSubmittingRoute = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final dateLabel = _travelDate == null
        ? 'Select date'
        : DateFormat('yyyy-MM-dd').format(_travelDate!);
    final timeLabel = _departureTime == null
        ? 'Select time'
        : _departureTime!.format(context);

    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        return SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    TextFormField(
                      controller: _originController,
                      decoration: const InputDecoration(labelText: 'Origin'),
                      validator: (value) {
                        final text = value?.trim() ?? '';
                        if (text.length < 2) {
                          return 'Minimum 2 characters';
                        }
                        return null;
                      },
                    ),
                    TextFormField(
                      controller: _destinationController,
                      decoration: const InputDecoration(labelText: 'Destination'),
                      validator: (value) {
                        final text = value?.trim() ?? '';
                        if (text.length < 2) {
                          return 'Minimum 2 characters';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      children: [
                        OutlinedButton(
                          onPressed: _pickDate,
                          child: Text(dateLabel),
                        ),
                        OutlinedButton(
                          onPressed: _pickTime,
                          child: Text(timeLabel),
                        ),
                      ],
                    ),
                    TextFormField(
                      controller: _seatCountController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Seat count (optional)',
                      ),
                      validator: (value) {
                        final text = value?.trim() ?? '';
                        if (text.isEmpty) {
                          return null;
                        }
                        final parsed = int.tryParse(text);
                        if (parsed == null || parsed < 1) {
                          return 'Enter a valid seat count';
                        }
                        return null;
                      },
                    ),
                    TextFormField(
                      controller: _notesController,
                      maxLines: 3,
                      decoration: const InputDecoration(labelText: 'Notes'),
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: _isSubmittingRoute ? null : _submit,
                      child: _isSubmittingRoute
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('Post route'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Text('My routes', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              if (_loadingMyRoutes)
                const Center(child: CircularProgressIndicator())
              else if (_myRoutesLoadError != null)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(_myRoutesLoadError!),
                    const SizedBox(height: 8),
                    OutlinedButton(
                      onPressed: _loadMyRoutes,
                      child: const Text('Retry'),
                    ),
                  ],
                )
              else if (widget.controller.myRoutes.isEmpty)
                const Text('No routes posted yet')
              else
                ...widget.controller.myRoutes.map(
                  (route) => Card(
                    child: ListTile(
                      title: Text('${route.origin} → ${route.destination}'),
                      subtitle: Text(
                        '${DateFormat('yyyy-MM-dd').format(route.travelDate)} • ${route.preferredDepartureTime}',
                      ),
                    ),
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}
