package com.oldrepublic.client.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.ui.NodeSocket;
import com.oldrepublic.client.ui.Ui;
import com.oldrepublic.client.ui.UiAnim;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillTree;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws one skill tree in a rectangle of the Force screen: node sockets on the grid from each node's
 * {@code pos}, scaled to fit, joined by thin links (lit along owned paths). The members of an
 * exclusive group fork from one small "or" diamond. Multi-rank nodes show rank pips under the
 * socket; path nodes the player is not on carry a "Jedi path" style label. Also answers which node
 * is under the cursor.
 */
final class TreeView {
	private static final int SIZE = NodeSocket.SIZE;
	private static final int PAD = 10;
	/** Room under a socket for rank pips or a path label. */
	private static final int UNDER = 11;
	private static final int MAX_COL = 46;
	private static final int MAX_ROW = 40;
	private static final int FLASH_TICKS = 24;

	/** Top-left corner of each node's socket, from the last {@link #layout}. */
	private final Map<String, int[]> positions = new HashMap<>();
	private @Nullable SkillTree tree;

	/** Places the nodes of {@code tree} inside the rectangle. */
	void layout(SkillTree tree, int x, int y, int w, int h) {
		this.tree = tree;
		positions.clear();
		float maxX = 0;
		float maxY = 0;

		for (SkillNode node : tree.nodes()) {
			maxX = Math.max(maxX, node.x());
			maxY = Math.max(maxY, node.y());
		}

		float col = maxX <= 0 ? 0 : Math.min(MAX_COL, (w - 2 * PAD - SIZE) / maxX);
		float row = maxY <= 0 ? 0 : Math.min(MAX_ROW, (h - 2 * PAD - SIZE - UNDER) / maxY);
		int gridW = Math.round(maxX * col) + SIZE;
		int gridH = Math.round(maxY * row) + SIZE + UNDER;
		int ox = x + Math.max(PAD, (w - gridW) / 2);
		int oy = y + Math.max(PAD, (h - gridH) / 2);

		for (SkillNode node : tree.nodes()) {
			positions.put(node.id(), new int[] {ox + Math.round(node.x() * col), oy + Math.round(node.y() * row)});
		}
	}

	/** The node under the cursor, or null. */
	@Nullable String nodeAt(double mouseX, double mouseY) {
		for (Map.Entry<String, int[]> e : positions.entrySet()) {
			int[] p = e.getValue();

			if (mouseX >= p[0] && mouseX < p[0] + SIZE && mouseY >= p[1] && mouseY < p[1] + SIZE) {
				return e.getKey();
			}
		}

		return null;
	}

	/** Top-left of a node's socket, or null when it is not in this tree. */
	int @Nullable [] position(String nodeId) {
		return positions.get(nodeId);
	}

	/**
	 * @param flashNode node that was just learned (a bright ring fades around it), or null
	 */
	void render(GuiGraphicsExtractor g, Font font, Map<String, NodeView> views, @Nullable String selected, @Nullable String hovered,
			@Nullable String flashNode, int flashAt, float time, float alpha) {
		if (tree == null) {
			return;
		}

		drawLinks(g, views, alpha);

		for (SkillNode node : tree.nodes()) {
			int[] p = positions.get(node.id());
			NodeView view = views.get(node.id());

			if (p == null || view == null) {
				continue;
			}

			NodeSocket.draw(g, p[0], p[1], node.icon(), view.state(), view.granted(), node.id().equals(selected), node.id().equals(hovered), time, alpha);
			int cx = p[0] + SIZE / 2;

			if (node.maxRank() > 1) {
				int pipsW = node.maxRank() * 6 - 1;
				int lit = view.state() == NodeSocket.State.DORMANT ? UiColors.AMBER_DIM : UiColors.HOLO;
				Ui.pips(g, cx - pipsW / 2, p[1] + SIZE + 2, node.maxRank(), view.rank(), lit, alpha);
			} else if (view.pathLabel() != null) {
				String label = Ui.fit(font, view.pathLabel().getString(), MAX_COL + 8);
				Ui.text(g, font, label, cx - font.width(label) / 2, p[1] + SIZE + 2, UiColors.TEXT_DIM, alpha);
			}

			if (node.id().equals(flashNode)) {
				float flash = UiAnim.envelope(time, flashAt, FLASH_TICKS, 2, FLASH_TICKS - 2);

				if (flash > 0f) {
					Ui.ring(g, p[0] - 3, p[1] - 3, SIZE + 6, SIZE + 6, 4, 1f, UiColors.HOLO_BRIGHT, 0, alpha * flash);
					Ui.ring(g, p[0] - 5, p[1] - 5, SIZE + 10, SIZE + 10, 5, 1f, UiColors.HOLO, 0, alpha * flash * 0.5f);
				}
			}
		}
	}

	/** Requirement links, drawn under the sockets. Exclusive groups fork from an "or" diamond. */
	private void drawLinks(GuiGraphicsExtractor g, Map<String, NodeView> views, float alpha) {
		Set<String> forked = new HashSet<>();

		for (SkillNode node : tree.nodes()) {
			int[] c = positions.get(node.id());

			for (SkillNode.Requirement req : node.requires()) {
				int[] p = positions.get(req.node());

				if (c == null || p == null) {
					continue;
				}

				if (node.group() != null) {
					if (forked.add(node.group() + "|" + req.node())) {
						drawFork(g, views, node.group(), req.node(), p, alpha);
					}

					continue;
				}

				drawLink(g, p, c, owned(views, req.node()) && owned(views, node.id()), alpha);
			}
		}
	}

	private void drawFork(GuiGraphicsExtractor g, Map<String, NodeView> views, String group, String parentId, int[] p, float alpha) {
		List<int[]> members = new ArrayList<>();
		List<Boolean> lit = new ArrayList<>();
		int minTop = Integer.MAX_VALUE;
		int sumX = 0;

		for (SkillNode member : tree.nodes()) {
			boolean fromParent = member.requires().stream().anyMatch(r -> r.node().equals(parentId));
			int[] m = positions.get(member.id());

			if (!group.equals(member.group()) || !fromParent || m == null) {
				continue;
			}

			members.add(m);
			lit.add(owned(views, member.id()));
			minTop = Math.min(minTop, m[1]);
			sumX += m[0] + SIZE / 2;
		}

		if (members.isEmpty()) {
			return;
		}

		int pcx = p[0] + SIZE / 2;
		int pBottom = p[1] + SIZE;

		if (minTop <= pBottom + 6) {
			// Members beside the parent: plain links.
			for (int i = 0; i < members.size(); i++) {
				drawLink(g, p, members.get(i), lit.get(i), alpha);
			}

			return;
		}

		int midY = (pBottom + minTop) / 2;
		int dx = sumX / members.size();
		boolean anyLit = lit.contains(Boolean.TRUE) && owned(views, parentId);
		NodeSocket.link(g, pcx, pBottom, pcx, midY, anyLit, alpha);
		NodeSocket.link(g, pcx, midY, dx, midY, anyLit, alpha);

		for (int i = 0; i < members.size(); i++) {
			int[] m = members.get(i);
			NodeSocket.link(g, dx, midY, m[0] + SIZE / 2, m[1], lit.get(i) && anyLit, alpha);
		}

		NodeSocket.orDiamond(g, dx, midY, anyLit, alpha);
	}

	/** Parent above: down to the middle, across, down. Same row: straight across. */
	private static void drawLink(GuiGraphicsExtractor g, int[] p, int[] c, boolean lit, float alpha) {
		int pcx = p[0] + SIZE / 2;
		int ccx = c[0] + SIZE / 2;

		if (c[1] > p[1] + SIZE) {
			int pBottom = p[1] + SIZE;
			int midY = (pBottom + c[1]) / 2;
			NodeSocket.link(g, pcx, pBottom, pcx, midY, lit, alpha);
			NodeSocket.link(g, pcx, midY, ccx, c[1], lit, alpha);
		} else if (Math.abs(c[1] - p[1]) < SIZE) {
			int cy = p[1] + SIZE / 2;
			int from = c[0] > p[0] ? p[0] + SIZE : c[0] + SIZE;
			int to = c[0] > p[0] ? c[0] - 1 : p[0] - 1;
			NodeSocket.link(g, from, cy, to, cy, lit, alpha);
		} else {
			NodeSocket.link(g, pcx, p[1] + SIZE / 2, ccx, c[1] + SIZE / 2, lit, alpha);
		}
	}

	private static boolean owned(Map<String, NodeView> views, String nodeId) {
		NodeView view = views.get(nodeId);
		return view != null && view.rank() > 0;
	}
}
