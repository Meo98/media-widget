"""Erzeugt die fünf Widget-Layouts und drei Vorschau-Layouts aus einer Beschreibung.
Aufruf: python3 gen_layouts.py <res/layout-Ordner>"""
import sys, os
out = sys.argv[1]
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
BG = '?android:attr/selectableItemBackgroundBorderless'

def btn(id_, src, size, desc, margin=0, gone=False, preview=False, tint=None):
    idattr = '' if preview and id_.startswith('action') else f'android:id="@+id/{id_}"\n        '
    return f'''<ImageButton
        {idattr}android:layout_width="{size}dp"
        android:layout_height="{size}dp"
        {f'android:layout_marginStart="{margin}dp"' if margin else ''}
        android:background="{BG}"
        android:src="@drawable/{src}"
        {f'android:tint="{tint}"' if tint else ''}
        {'android:visibility="gone"' if gone else ''}
        android:contentDescription="@string/{desc}"/>'''

def transport(sizes, gap, p):
    t = '@color/material_button_tint' if p else None
    s, b = sizes
    return '\n'.join([
        btn('btn_prev', 'ic_prev', s, 'prev_desc', preview=p, tint=t),
        btn('btn_play_pause', 'ic_play', b, 'play_pause_desc', gap, preview=p, tint=t),
        btn('btn_next', 'ic_next', s, 'next_desc', gap, preview=p, tint=t)])

def actions(n, size, gap, first_margin=0):
    return '\n'.join(btn(f'action_slot_{i}', 'ic_heart', size, 'action_slot_desc',
                         first_margin if i == 1 else gap, gone=True) for i in range(1, n + 1))

def text(id_, sp, bold, p, center=False):
    sample = {'title': '@string/preview_title', 'artist': '@string/preview_artist'}[id_]
    idle = {'title': '@string/idle_title', 'artist': '@string/idle_artist'}[id_]
    color = {'title': '@color/material_text_primary', 'artist': '@color/material_text_secondary'}[id_]
    return f'''<TextView
        android:id="@+id/{id_}"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="{sp}sp"
        {'android:textStyle="bold"' if bold else ''}
        {'android:gravity="center_horizontal"' if center else ''}
        {f'android:textColor="{color}"' if p else ''}
        android:singleLine="true"
        android:ellipsize="end"
        android:text="{sample if p else idle}"/>'''

def cover(w, h, p, extra=''):
    return f'''<ImageView
        android:id="@+id/cover"
        android:clipToOutline="true"
        android:layout_width="{w}"
        android:layout_height="{h}"
        {extra}
        android:scaleType="centerCrop"
        android:background="@drawable/widget_cover_background"
        android:src="@drawable/{'preview_cover' if p else 'ic_music'}"
        android:contentDescription="@string/cover_desc"/>'''

SQUARE = 'android:adjustViewBounds="true"'

def switcher(size, inline=False):
    icons = '\n'.join(f'''<ImageView
            android:id="@+id/app_switcher_{i}"
            android:layout_width="{size}dp"
            android:layout_height="{size}dp"
            {'android:layout_marginStart="4dp"' if i > 1 else ''}
            android:scaleType="fitCenter"
            android:visibility="gone"
            android:contentDescription="@string/app_switcher_desc"/>''' for i in (1, 2, 3))
    pos = 'android:layout_marginStart="8dp"' if inline else 'android:layout_gravity="top|end"\n        android:layout_margin="8dp"'
    return f'''<LinearLayout
        android:id="@+id/app_switcher_row"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        {pos}
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:visibility="gone">
        {icons}
    </LinearLayout>'''

PROGRESS = '''<ProgressBar
        android:id="@+id/progress"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="4dp"
        android:layout_marginTop="8dp"
        android:max="100"
        android:progress="0"
        android:visibility="gone"/>'''
SPACER = '<FrameLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"/>'

def root(orientation, padding, body, overlay='', gravity=''):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Erzeugt aus einer gemeinsamen Beschreibung; alle Stufen haben dieselben View-Ids. -->
<FrameLayout {NS}
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <ImageView
        android:id="@+id/backdrop"
        android:clipToOutline="true"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:visibility="gone"
        android:importantForAccessibility="no"/>
    <LinearLayout
        android:id="@+id/container"
        android:clipToOutline="true"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="{orientation}"
        android:padding="{padding}dp"
        {gravity}
        android:background="@drawable/widget_background">
        {body}
    </LinearLayout>
    {overlay}
</FrameLayout>
'''

def column(body, margin):
    return f'''<LinearLayout
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:layout_marginStart="{margin}dp"
        android:orientation="vertical">
        {body}
    </LinearLayout>'''

def row(body, top=0, center=False, id_=None, gone=False):
    return f'''<LinearLayout
        {f'android:id="@+id/{id_}"' if id_ else ''}
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        {f'android:layout_marginTop="{top}dp"' if top else ''}
        {'android:layout_gravity="center_horizontal"' if center else ''}
        android:gravity="center_vertical"
        {'android:visibility="gone"' if gone else ''}
        android:orientation="horizontal">
        {body}
    </LinearLayout>'''

def texts_col(p, tsp, asp):
    return f'''<LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="10dp"
        android:orientation="vertical">
        {text('title', tsp, True, p)}
        {text('artist', asp, False, p)}
    </LinearLayout>'''

def microbar(p=False):
    t = '@color/material_button_tint' if p else None
    return root('horizontal', 8, cover('44dp', '44dp', p) + texts_col(p, 13, 11) +
                btn('btn_play_pause', 'ic_play', 40, 'play_pause_desc', 4, preview=p, tint=t),
                gravity='android:gravity="center_vertical"')

def bar(p=False):
    body = cover('52dp', '52dp', p) + texts_col(p, 14, 12) + transport((36, 40), 2, p)
    if not p:
        body += actions(1, 32, 0, 4) + switcher(22, inline=True)
    return root('horizontal', 10, body, gravity='android:gravity="center_vertical"')

def card(p, tsp, asp, sizes, n_actions, asize, pad):
    """Cover links (quadratisch, so hoch wie das Widget), rechts Text und Knöpfe."""
    right = text('title', tsp, True, p) + text('artist', asp, False, p) + SPACER + row(transport(sizes, 6, p))
    if not p:
        right += row(actions(n_actions, asize, 6), top=6, id_='actions_row', gone=True) + PROGRESS
    return root('horizontal', pad, cover('wrap_content', 'match_parent', p, SQUARE) + column(right, pad),
                overlay='' if p else switcher(22))

def tall(p=False):
    """Hochformat: Cover oben, darunter Text und Knöpfe."""
    body = cover('match_parent', '0dp', p, 'android:layout_weight="1"') + \
        '<FrameLayout android:layout_width="match_parent" android:layout_height="8dp"/>' + \
        text('title', 15, True, p, center=True) + text('artist', 13, False, p, center=True) + \
        row(transport((40, 44), 8, p), top=6, center=True)
    if not p:
        body += row(actions(4, 34, 6), top=4, center=True, id_='actions_row', gone=True) + PROGRESS
    return root('vertical', 12, body, overlay='' if p else switcher(22))

files = {
    'widget_microbar.xml': microbar(),
    'widget_bar.xml': bar(),
    'widget_midcard.xml': card(False, 15, 13, (38, 42), 2, 32, 12),
    'widget_wide.xml': tall(),
    'widget_mega.xml': card(False, 17, 14, (42, 48), 4, 36, 14),
    'preview_bar.xml': bar(True),
    'preview_card.xml': card(True, 15, 13, (38, 42), 0, 0, 12),
    'preview_large.xml': tall(True),
}
for name, xml in files.items():
    xml = '\n'.join(l for l in xml.split('\n') if l.strip())
    open(os.path.join(out, name), 'w').write(xml + '\n')
    print(name, xml.count('\n'))
