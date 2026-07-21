package com.marmore.api.image.domain;

/** Prompts fixos de edicao. Textos que o produto emprega sem entrada do cliente. */
public final class EditPrompts {

  /**
   * Prompt de bancada suspensa em granito com cuba embutida e escorredor rebaixado. Referencia
   * IMAGE 1 (ambiente) e IMAGE 2 (granito). Ordem do multipart no service deve respeitar essa
   * convencão.
   */
  // CHECKSTYLE.SUPPRESS: LineLength for +24 lines
  public static final String COUNTERTOP =
      """
      I am sending two images. IMAGE 1: a photo of a real environment (the base scene). IMAGE 2: a close-up swatch of the exact granite material for the new countertop. IMAGE 2 is the single source of truth for the countertop's color and texture — it shows only the material, not a scene, and must not appear as an object in the result.

      BASE SCENE (IMAGE 1): a real environment. Preserve the entire scene exactly as it appears — walls, floor, windows, doors, wall openings, the room in the background, existing decorations, the natural lighting, and the camera angle. Do not repaint or alter any surface.

      CLEAR ONLY THE INSTALLATION AREA: the new countertop will be installed against the main wall (centered below the wall opening, if there is one). Remove every piece of furniture and every loose object inside or overlapping this installation area — tables, chairs, stools, cloths, plants and pots, footwear, papers, animals — so that the wall behind the countertop and the floor beneath it are completely bare and clean. Since the countertop is floating with open space underneath, nothing may remain visible under it or immediately around it: only bare wall and clean floor. Everything outside this area stays exactly as it is. Also remove any watermark, date/time stamp, or text overlay anywhere in the image.

      ADD: a wall-mounted floating countertop made of the exact stone shown in IMAGE 2, in the cleared installation area, against the main wall — centered below the wall opening if there is one. Floating slab fixed directly to the wall — no legs, no side supports, open space underneath showing bare wall and clean floor. Counter height (~80 cm), spanning most of the wall width, with a thick front edge and a low backsplash against the wall.

      SUNKEN DRAINBOARD — THE MOST IMPORTANT STRUCTURAL ELEMENT (a sunken rectangular drainboard area carved into the granite countertop):
      - A rectangle carved into the slab, sitting clearly LOWER than the main surface — a real depression, about 2–3 cm deep, like a shallow stone tray. NOT a flat engraved outline, NOT just a thin border line: the surface must actually step down.
      - The recess has visible inner vertical walls around its entire perimeter, where the top surface drops to the lower level. The far inner wall casts a soft shadow onto the recessed floor; the reflections break sharply at the step edge.
      - The recessed floor reflects light differently from the main surface — slightly darker and with its own reflections — so the depth is obvious at first glance.
      - The rectangle occupies the central half of the countertop's length, with equal full-height margins on the left and on the right, and narrow full-height strips at the front and at the back, next to the backsplash.
      - Square corners, edges parallel to the countertop edges, polished, with a slight slope toward the sink.

      SINK: a single rectangular stainless steel undermount sink at the exact center of the sunken area, rim below the sunken surface (no top-mount sink). The sunken surface around it works as a drainboard, with equal margins on both sides. Sink, sunken area, and countertop share the same central axis.

      GRANITE MATERIAL — COPY FROM IMAGE 2 (HIGHEST PRIORITY): the entire countertop (slab, front edge, backsplash, inner walls and floor of the sunken area) must look as if it was cut from the exact stone photographed in IMAGE 2. Match IMAGE 2's hue, saturation, crystal pattern, and speckle density precisely — unmistakably GREEN at first glance. If any written description conflicts with IMAGE 2, follow IMAGE 2. Never gray, never plain black, never beige.

      Photorealistic result, same camera angle and same lighting as IMAGE 1. Two things must be clearly visible at first glance: the green granite from IMAGE 2, and the sunken step of the drainboard around the sink.
      """;

  private EditPrompts() {}
}
