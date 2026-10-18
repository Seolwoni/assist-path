/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seolwoni.assistpath.calc;

/**
 * What a path search returned.
 *
 * Modified for Assist Path (2026), after Baritone's PathCalculationResult.
 */
public record PathResult(Type type, Path path) {
    public enum Type {
        SUCCESS_TO_GOAL, SUCCESS_SEGMENT, FAILURE, CANCELLATION, EXCEPTION
    }

    public boolean found() {
        return path != null && (type == Type.SUCCESS_TO_GOAL || type == Type.SUCCESS_SEGMENT);
    }
}
