import 'package:flutter/material.dart';
import 'package:route_mates_mobile/features/shared/models/models.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class ProfileTab extends StatefulWidget {
  const ProfileTab({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  State<ProfileTab> createState() => _ProfileTabState();
}

class _ProfileTabState extends State<ProfileTab> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _cityController = TextEditingController();
  final _bioController = TextEditingController();
  final _avatarUrlController = TextEditingController();
  String? _gender;
  String? _boundProfileId;

  @override
  void initState() {
    super.initState();
    widget.controller.fetchProfile();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _phoneController.dispose();
    _cityController.dispose();
    _bioController.dispose();
    _avatarUrlController.dispose();
    super.dispose();
  }

  void _bindProfile(UserProfile profile) {
    if (_boundProfileId == profile.id) {
      return;
    }

    _boundProfileId = profile.id;
    _nameController.text = profile.name;
    _phoneController.text = profile.phone ?? '';
    _cityController.text = profile.city ?? '';
    _bioController.text = profile.bio ?? '';
    _avatarUrlController.text = profile.avatarUrl ?? '';
    _gender = profile.gender;
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    final current = widget.controller.profile;
    if (current == null) {
      return;
    }

    final payload = <String, dynamic>{};
    final name = _nameController.text.trim();
    final phone = _phoneController.text.trim();
    final city = _cityController.text.trim();
    final bio = _bioController.text.trim();
    final avatarUrl = _avatarUrlController.text.trim();

    if (name.isNotEmpty) {
      payload['name'] = name;
    }
    if (phone.isNotEmpty) {
      payload['phone'] = phone;
    }
    if (city.isNotEmpty) {
      payload['city'] = city;
    }
    if (_gender != null && _gender!.isNotEmpty) {
      payload['gender'] = _gender;
    }
    if (bio != (current.bio ?? '')) {
      payload['bio'] = bio;
    }
    if (avatarUrl.isNotEmpty) {
      payload['avatarUrl'] = avatarUrl;
    }

    try {
      await widget.controller.updateProfile(payload);
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Profile updated')));
    } catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final profile = widget.controller.profile;

        if (profile == null) {
          return const Center(child: CircularProgressIndicator());
        }

        _bindProfile(profile);

        return SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Email: ${profile.email}'),
                const SizedBox(height: 8),
                Text(
                  profile.isProfileComplete
                      ? 'Profile complete'
                      : 'Profile incomplete',
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _nameController,
                  decoration: const InputDecoration(labelText: 'Name'),
                  validator: (value) {
                    final text = value?.trim() ?? '';
                    if (text.isEmpty || text.length >= 2) {
                      return null;
                    }
                    return 'Minimum 2 characters';
                  },
                ),
                TextFormField(
                  controller: _phoneController,
                  decoration: const InputDecoration(labelText: 'Phone'),
                ),
                TextFormField(
                  controller: _cityController,
                  decoration: const InputDecoration(labelText: 'City'),
                  validator: (value) {
                    final text = value?.trim() ?? '';
                    if (text.isEmpty || text.length >= 2) {
                      return null;
                    }
                    return 'Minimum 2 characters';
                  },
                ),
                DropdownButtonFormField<String>(
                  initialValue: _gender,
                  decoration: const InputDecoration(labelText: 'Gender'),
                  items: const [
                    DropdownMenuItem(value: 'male', child: Text('Male')),
                    DropdownMenuItem(value: 'female', child: Text('Female')),
                    DropdownMenuItem(
                      value: 'non_binary',
                      child: Text('Non-binary'),
                    ),
                    DropdownMenuItem(
                      value: 'prefer_not_to_say',
                      child: Text('Prefer not to say'),
                    ),
                  ],
                  onChanged: (value) {
                    setState(() {
                      _gender = value;
                    });
                  },
                ),
                TextFormField(
                  controller: _bioController,
                  decoration: const InputDecoration(labelText: 'Bio'),
                  maxLines: 3,
                ),
                TextFormField(
                  controller: _avatarUrlController,
                  decoration: const InputDecoration(labelText: 'Avatar URL'),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: widget.controller.isSubmitting ? null : _submit,
                  child: widget.controller.isSubmitting
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Save profile'),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
