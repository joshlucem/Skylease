/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.models;

import org.bukkit.Particle;

/** An effect from effects.yml. Its name and description are in the lang files. */
public record ParticleEffect(String id, String material, String permission, Particle particle) {
}
