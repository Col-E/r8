# Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

.class Landroid/databinding/DataBinderMapperImpl;
.super Landroid/databinding/DataBinderMapper;
.source "DataBinderMapperImpl.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Landroid/databinding/DataBinderMapperImpl$InnerBrLookup;
    }
.end annotation


# direct methods
.method public constructor <init>()V
    .registers 1

    .line 5
    invoke-direct {p0}, Landroid/databinding/DataBinderMapper;-><init>()V

    .line 6
    return-void
.end method


# virtual methods
.method public convertBrIdToString(I)Ljava/lang/String;
    .registers 3
    .param p1, "id"    # I

    .line 4474
    if-ltz p1, :cond_d

    sget-object v0, Landroid/databinding/DataBinderMapperImpl$InnerBrLookup;->sKeys:[Ljava/lang/String;

    array-length v0, v0

    if-lt p1, v0, :cond_8

    goto :goto_d

    .line 4477
    :cond_8
    sget-object v0, Landroid/databinding/DataBinderMapperImpl$InnerBrLookup;->sKeys:[Ljava/lang/String;

    aget-object v0, v0, p1

    return-object v0

    .line 4475
    :cond_d
    :goto_d
    const/4 v0, 0x0

    return-object v0
.end method

.method public getDataBinder(Landroid/databinding/DataBindingComponent;Landroid/view/View;I)Landroid/databinding/ViewDataBinding;
    .registers 8
    .param p1, "bindingComponent"    # Landroid/databinding/DataBindingComponent;
    .param p2, "view"    # Landroid/view/View;
    .param p3, "layoutId"    # I

    .line 9
    packed-switch p3, :pswitch_data_3bcc

    packed-switch p3, :pswitch_data_3bd6

    packed-switch p3, :pswitch_data_3be2

    packed-switch p3, :pswitch_data_3bea

    packed-switch p3, :pswitch_data_3bf8

    packed-switch p3, :pswitch_data_3c00

    packed-switch p3, :pswitch_data_3c10

    packed-switch p3, :pswitch_data_3c18

    packed-switch p3, :pswitch_data_3c22

    packed-switch p3, :pswitch_data_3c2a

    packed-switch p3, :pswitch_data_3c32

    packed-switch p3, :pswitch_data_3c40

    packed-switch p3, :pswitch_data_3c4c

    packed-switch p3, :pswitch_data_3c58

    packed-switch p3, :pswitch_data_3c62

    packed-switch p3, :pswitch_data_3c72

    packed-switch p3, :pswitch_data_3c8a

    packed-switch p3, :pswitch_data_3cd2

    packed-switch p3, :pswitch_data_3cde

    packed-switch p3, :pswitch_data_3d2a

    packed-switch p3, :pswitch_data_3d92

    packed-switch p3, :pswitch_data_3d9a

    packed-switch p3, :pswitch_data_3da8

    packed-switch p3, :pswitch_data_3db2

    packed-switch p3, :pswitch_data_3dd2

    packed-switch p3, :pswitch_data_3de6

    packed-switch p3, :pswitch_data_3df2

    packed-switch p3, :pswitch_data_3dfc

    packed-switch p3, :pswitch_data_3e08

    packed-switch p3, :pswitch_data_3e12

    packed-switch p3, :pswitch_data_3e20

    packed-switch p3, :pswitch_data_3e2a

    packed-switch p3, :pswitch_data_3e34

    packed-switch p3, :pswitch_data_3e42

    packed-switch p3, :pswitch_data_3e52

    sparse-switch p3, :sswitch_data_3e5c

    .line 2648
    const/4 v0, 0x0

    return-object v0

    .line 1779
    :sswitch_6e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1780
    .local v0, "tag":Ljava/lang/Object;
    if-nez v0, :cond_7d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1781
    :cond_7d
    const-string v1, "layout/toolbar_onboarding_progress_layout_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_8b

    .line 1782
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarOnboardingProgressLayoutBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarOnboardingProgressLayoutBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1784
    :cond_8b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_onboarding_progress_layout is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 825
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_a2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 826
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_b1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 827
    :cond_b1
    const-string v1, "layout/toolbar_gray_layout_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_bf

    .line 828
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarGrayLayoutBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarGrayLayoutBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 830
    :cond_bf
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_gray_layout is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 942
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_d6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 943
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_e5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 944
    :cond_e5
    const-string v1, "layout/toolbar_base_layout_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_f3

    .line 945
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarBaseLayoutBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarBaseLayoutBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 947
    :cond_f3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_base_layout is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2259
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_10a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2260
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_119

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2261
    :cond_119
    const-string v1, "layout/thumbnail_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_127

    .line 2262
    new-instance v1, Lcom/classdojo/components/databinding/ThumbnailItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/components/databinding/ThumbnailItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2264
    :cond_127
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for thumbnail_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1368
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_13e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1369
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_14d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1370
    :cond_14d
    const-string v1, "layout/story_post_created_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_15b

    .line 1371
    new-instance v1, Lcom/classdojo/android/databinding/StoryPostCreatedBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/StoryPostCreatedBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1373
    :cond_15b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for story_post_created is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 315
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_172
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 316
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_181

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 317
    :cond_181
    const-string v1, "layout/setup_skills_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_18f

    .line 318
    new-instance v1, Lcom/classdojo/android/databinding/SetupSkillsListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/SetupSkillsListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 320
    :cond_18f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for setup_skills_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1518
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_1a6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1519
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1b5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1520
    :cond_1b5
    const-string v1, "layout/popup_item_copy_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1c3

    .line 1521
    new-instance v1, Lcom/classdojo/android/databinding/PopupItemCopyBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/PopupItemCopyBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1523
    :cond_1c3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for popup_item_copy is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 585
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_1da
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 586
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1e9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 587
    :cond_1e9
    const-string v1, "layout/placeholder_offline_with_refresh_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1f7

    .line 588
    new-instance v1, Lcom/classdojo/android/databinding/PlaceholderOfflineWithRefreshBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/PlaceholderOfflineWithRefreshBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 590
    :cond_1f7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for placeholder_offline_with_refresh is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 795
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_20e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 796
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_21d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 797
    :cond_21d
    const-string v1, "layout/placeholder_empty_with_refresh_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_22b

    .line 798
    new-instance v1, Lcom/classdojo/android/databinding/PlaceholderEmptyWithRefreshBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/PlaceholderEmptyWithRefreshBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 800
    :cond_22b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for placeholder_empty_with_refresh is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1473
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_242
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1474
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_251

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1475
    :cond_251
    const-string v1, "layout/item_teacher_add_coteacher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_25f

    .line 1476
    new-instance v1, Lcom/classdojo/android/databinding/ItemTeacherAddCoteacherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemTeacherAddCoteacherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1478
    :cond_25f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_teacher_add_coteacher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1680
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_276
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1681
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_285

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1682
    :cond_285
    const-string v1, "layout/item_student_drawing_tool_sticker_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_293

    .line 1683
    new-instance v1, Lcom/classdojo/android/databinding/ItemStudentDrawingToolStickerBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemStudentDrawingToolStickerBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1685
    :cond_293
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_student_drawing_tool_sticker is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1161
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_2aa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1162
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2b9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1163
    :cond_2b9
    const-string v1, "layout/item_parent_list_new_message_invite_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2c7

    .line 1164
    new-instance v1, Lcom/classdojo/android/databinding/ItemParentListNewMessageInviteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemParentListNewMessageInviteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1166
    :cond_2c7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_parent_list_new_message_invite is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1044
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_2de
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1045
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ed

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1046
    :cond_2ed
    const-string v1, "layout/item_image_resource_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2fb

    .line 1047
    new-instance v1, Lcom/classdojo/android/databinding/ItemImageResourceBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemImageResourceBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1049
    :cond_2fb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_image_resource is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1863
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_312
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1864
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_321

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1865
    :cond_321
    const-string v1, "layout/item_add_student_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_32f

    .line 1866
    new-instance v1, Lcom/classdojo/android/databinding/ItemAddStudentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemAddStudentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1868
    :cond_32f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_add_student is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1287
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_346
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1288
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_355

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1289
    :cond_355
    const-string v1, "layout/fragment_tab_class_wall_item_student_avatar_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_363

    .line 1290
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemStudentAvatarBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemStudentAvatarBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1292
    :cond_363
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_student_avatar is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 351
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_37a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 352
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_389

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 353
    :cond_389
    const-string v1, "layout/fragment_tab_class_wall_generic_button_icon_text_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_397

    .line 354
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallGenericButtonIconTextItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallGenericButtonIconTextItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 356
    :cond_397
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_generic_button_icon_text_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 648
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_3ae
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 649
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3bd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 650
    :cond_3bd
    const-string v1, "layout/fragment_school_detail_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3cb

    .line 651
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDetailBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDetailBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 653
    :cond_3cb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_detail is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1815
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_3e2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1816
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3f1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1817
    :cond_3f1
    const-string v1, "layout/fragment_group_students_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3ff

    .line 1818
    new-instance v1, Lcom/classdojo/android/databinding/FragmentGroupStudentsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentGroupStudentsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1820
    :cond_3ff
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_group_students is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 567
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_416
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 568
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_425

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 569
    :cond_425
    const-string v1, "layout/fragment_account_switcher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_433

    .line 570
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAccountSwitcherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAccountSwitcherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 572
    :cond_433
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_account_switcher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 978
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_44a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 979
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_459

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 980
    :cond_459
    const-string v1, "layout/debug_feature_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_467

    .line 981
    new-instance v1, Lcom/classdojo/android/databinding/DebugFeatureListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DebugFeatureListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 983
    :cond_467
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for debug_feature_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1644
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_47e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1645
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_48d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1646
    :cond_48d
    const-string v1, "layout/chat_empty_broadcasts_view_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_49b

    .line 1647
    new-instance v1, Lcom/classdojo/android/databinding/ChatEmptyBroadcastsViewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ChatEmptyBroadcastsViewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1649
    :cond_49b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for chat_empty_broadcasts_view is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1425
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_4b2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1426
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_4c1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1427
    :cond_4c1
    const-string v1, "layout-sw600dp-land/activity_student_capture_home_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_4cf

    .line 1428
    new-instance v1, Lcom/classdojo/android/databinding/ActivityStudentCaptureHomeBindingSw600dpLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityStudentCaptureHomeBindingSw600dpLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1430
    :cond_4cf
    const-string v1, "layout/activity_student_capture_home_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_4dd

    .line 1431
    new-instance v1, Lcom/classdojo/android/databinding/ActivityStudentCaptureHomeBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityStudentCaptureHomeBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1433
    :cond_4dd
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_student_capture_home is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2349
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_4f4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2350
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_503

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2351
    :cond_503
    const-string v1, "layout/activity_school_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_511

    .line 2352
    new-instance v1, Lcom/classdojo/android/databinding/ActivitySchoolBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivitySchoolBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2354
    :cond_511
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_school is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 495
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_528
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 496
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_537

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 497
    :cond_537
    const-string v1, "layout/activity_qrcode_scan_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_545

    .line 498
    new-instance v1, Lcom/classdojo/android/databinding/ActivityQrcodeScanBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityQrcodeScanBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 500
    :cond_545
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_qrcode_scan is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 333
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_55c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 334
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_56b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 335
    :cond_56b
    const-string v1, "layout/activity_passwordless_login_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_579

    .line 336
    new-instance v1, Lcom/classdojo/android/databinding/ActivityPasswordlessLoginBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityPasswordlessLoginBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 338
    :cond_579
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_passwordless_login is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 960
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_590
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 961
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_59f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 962
    :cond_59f
    const-string v1, "layout/activity_parent_setup_student_account_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_5ad

    .line 963
    new-instance v1, Lcom/classdojo/android/databinding/ActivityParentSetupStudentAccountBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityParentSetupStudentAccountBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 965
    :cond_5ad
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_parent_setup_student_account is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1572
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_5c4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1573
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_5d3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1574
    :cond_5d3
    const-string v1, "layout/activity_parent_home_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_5e1

    .line 1575
    new-instance v1, Lcom/classdojo/android/databinding/ActivityParentHomeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityParentHomeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1577
    :cond_5e1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_parent_home is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1635
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_5f8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1636
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_607

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1637
    :cond_607
    const-string v1, "layout/activity_parent_checklist_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_615

    .line 1638
    new-instance v1, Lcom/classdojo/android/databinding/ActivityParentChecklistBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityParentChecklistBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1640
    :cond_615
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_parent_checklist is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2520
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_62c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2521
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_63b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2522
    :cond_63b
    const-string v1, "layout/activity_email_verified_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_649

    .line 2523
    new-instance v1, Lcom/classdojo/android/databinding/ActivityEmailVerifiedBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityEmailVerifiedBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2525
    :cond_649
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_email_verified is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1797
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_660
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1798
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_66f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1799
    :cond_66f
    const-string v1, "layout/activity_class_link_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_67d

    .line 1800
    new-instance v1, Lcom/classdojo/android/databinding/ActivityClassLinkBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityClassLinkBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1802
    :cond_67d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_class_link is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 180
    .end local v0    # "tag":Ljava/lang/Object;
    :sswitch_694
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 181
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_6a3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 182
    :cond_6a3
    const-string v1, "layout/activity_add_edit_class_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_6b1

    .line 183
    new-instance v1, Lcom/classdojo/android/databinding/ActivityAddEditClassBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityAddEditClassBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 185
    :cond_6b1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_add_edit_class is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1617
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_6c8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1618
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_6d7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1619
    :cond_6d7
    const-string v1, "layout/webview_fragment_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_6e5

    .line 1620
    new-instance v1, Lcom/classdojo/android/databinding/WebviewFragmentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/WebviewFragmentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1622
    :cond_6e5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for webview_fragment is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 30
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_6fc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 31
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_70b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 32
    :cond_70b
    const-string v1, "layout/view_teacher_student_connection_text_codes_instructions_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_719

    .line 33
    new-instance v1, Lcom/classdojo/android/databinding/ViewTeacherStudentConnectionTextCodesInstructionsHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewTeacherStudentConnectionTextCodesInstructionsHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 35
    :cond_719
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_teacher_student_connection_text_codes_instructions_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1743
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_730
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1744
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_73f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1745
    :cond_73f
    const-string v1, "layout/view_students_moved_tooltip_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_74d

    .line 1746
    new-instance v1, Lcom/classdojo/android/databinding/ViewStudentsMovedTooltipBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewStudentsMovedTooltipBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1748
    :cond_74d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_students_moved_tooltip is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2250
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_764
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2251
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_773

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2252
    :cond_773
    const-string v1, "layout/view_parent_pending_connection_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_781

    .line 2253
    new-instance v1, Lcom/classdojo/android/databinding/ViewParentPendingConnectionBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewParentPendingConnectionBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2255
    :cond_781
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_parent_pending_connection is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2049
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_798
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2050
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_7a7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2051
    :cond_7a7
    const-string v1, "layout/view_list_header_limit_width_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_7b5

    .line 2052
    new-instance v1, Lcom/classdojo/android/databinding/ViewListHeaderLimitWidthBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewListHeaderLimitWidthBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2054
    :cond_7b5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_list_header_limit_width is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 207
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_7cc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 208
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_7db

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 209
    :cond_7db
    const-string v1, "layout/view_list_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_7e9

    .line 210
    new-instance v1, Lcom/classdojo/android/databinding/ViewListHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewListHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 212
    :cond_7e9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_list_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1599
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_800
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1600
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_80f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1601
    :cond_80f
    const-string v1, "layout/view_drawing_tool_sticker_drawer_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_81d

    .line 1602
    new-instance v1, Lcom/classdojo/android/databinding/ViewDrawingToolStickerDrawerBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewDrawingToolStickerDrawerBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1604
    :cond_81d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_drawing_tool_sticker_drawer is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2538
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_834
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2539
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_843

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2540
    :cond_843
    const-string v1, "layout/view_drawing_tool_discard_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_851

    .line 2541
    new-instance v1, Lcom/classdojo/android/databinding/ViewDrawingToolDiscardBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewDrawingToolDiscardBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2543
    :cond_851
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_drawing_tool_discard is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2610
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_868
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2611
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_877

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2612
    :cond_877
    const-string v1, "layout/view_drawer_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_885

    .line 2613
    new-instance v1, Lcom/classdojo/android/databinding/ViewDrawerItemBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewDrawerItemBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2615
    :cond_885
    const-string v1, "layout-sw600dp-land/view_drawer_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_893

    .line 2616
    new-instance v1, Lcom/classdojo/android/databinding/ViewDrawerItemBindingSw600dpLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ViewDrawerItemBindingSw600dpLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2618
    :cond_893
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for view_drawer_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2286
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_8aa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2287
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_8b9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2288
    :cond_8b9
    const-string v1, "layout/toolbar_text_post_layout_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_8c7

    .line 2289
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarTextPostLayoutBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarTextPostLayoutBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2291
    :cond_8c7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_text_post_layout is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 441
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_8de
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 442
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_8ed

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 443
    :cond_8ed
    const-string v1, "layout/toolbar_text_layout_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_8fb

    .line 444
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarTextLayoutBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarTextLayoutBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 446
    :cond_8fb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_text_layout is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1824
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_912
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1825
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_921

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1826
    :cond_921
    const-string v1, "layout/toolbar_teacher_onboarding_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_92f

    .line 1827
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarTeacherOnboardingBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarTeacherOnboardingBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1829
    :cond_92f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_teacher_onboarding is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 924
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_946
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 925
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_955

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 926
    :cond_955
    const-string v1, "layout/toolbar_teacher_home_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_963

    .line 927
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarTeacherHomeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarTeacherHomeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 929
    :cond_963
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_teacher_home is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1377
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_97a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1378
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_989

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1379
    :cond_989
    const-string v1, "layout/toolbar_teacher_approval_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_997

    .line 1380
    new-instance v1, Lcom/classdojo/android/databinding/ToolbarTeacherApprovalBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ToolbarTeacherApprovalBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1382
    :cond_997
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for toolbar_teacher_approval is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1698
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_9ae
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1699
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_9bd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1700
    :cond_9bd
    const-string v1, "layout/student_login_list_not_my_class_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_9cb

    .line 1701
    new-instance v1, Lcom/classdojo/android/databinding/StudentLoginListNotMyClassItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/StudentLoginListNotMyClassItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1703
    :cond_9cb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for student_login_list_not_my_class_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1842
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_9e2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1843
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_9f1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1844
    :cond_9f1
    const-string v1, "layout-sw600dp/student_login_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_9ff

    .line 1845
    new-instance v1, Lcom/classdojo/android/databinding/StudentLoginListItemBindingSw600dpImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/StudentLoginListItemBindingSw600dpImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1847
    :cond_9ff
    const-string v1, "layout/student_login_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_a0d

    .line 1848
    new-instance v1, Lcom/classdojo/android/databinding/StudentLoginListItemBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/StudentLoginListItemBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1850
    :cond_a0d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for student_login_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2124
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_a24
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2125
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_a33

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2126
    :cond_a33
    const-string v1, "layout/student_connections_individual_codes_invite_section_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_a41

    .line 2127
    new-instance v1, Lcom/classdojo/android/databinding/StudentConnectionsIndividualCodesInviteSectionBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/StudentConnectionsIndividualCodesInviteSectionBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2129
    :cond_a41
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for student_connections_individual_codes_invite_section is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1224
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_a58
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1225
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_a67

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1226
    :cond_a67
    const-string v1, "layout/parent_connections_single_code_invite_section_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_a75

    .line 1227
    new-instance v1, Lcom/classdojo/android/databinding/ParentConnectionsSingleCodeInviteSectionBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ParentConnectionsSingleCodeInviteSectionBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1229
    :cond_a75
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for parent_connections_single_code_invite_section is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1833
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_a8c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1834
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_a9b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1835
    :cond_a9b
    const-string v1, "layout/parent_connections_individual_codes_invite_section_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_aa9

    .line 1836
    new-instance v1, Lcom/classdojo/android/databinding/ParentConnectionsIndividualCodesInviteSectionBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ParentConnectionsIndividualCodesInviteSectionBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1838
    :cond_aa9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for parent_connections_individual_codes_invite_section is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 216
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_ac0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 217
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_acf

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 218
    :cond_acf
    const-string v1, "layout/parent_checklist_success_overlay_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_add

    .line 219
    new-instance v1, Lcom/classdojo/android/databinding/ParentChecklistSuccessOverlayBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ParentChecklistSuccessOverlayBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 221
    :cond_add
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for parent_checklist_success_overlay is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1890
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_af4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1891
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_b03

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1892
    :cond_b03
    const-string v1, "layout/layout_student_list_empty_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_b11

    .line 1893
    new-instance v1, Lcom/classdojo/android/databinding/LayoutStudentListEmptyBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutStudentListEmptyBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1895
    :cond_b11
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_student_list_empty is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2214
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_b28
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2215
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_b37

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2216
    :cond_b37
    const-string v1, "layout/layout_student_connections_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_b45

    .line 2217
    new-instance v1, Lcom/classdojo/android/databinding/LayoutStudentConnectionsListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutStudentConnectionsListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2219
    :cond_b45
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_student_connections_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1395
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_b5c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1396
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_b6b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1397
    :cond_b6b
    const-string v1, "layout/layout_student_connections_invite_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_b79

    .line 1398
    new-instance v1, Lcom/classdojo/android/databinding/LayoutStudentConnectionsInviteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutStudentConnectionsInviteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1400
    :cond_b79
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_student_connections_invite is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1170
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_b90
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1171
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_b9f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1172
    :cond_b9f
    const-string v1, "layout/layout_student_connections_initial_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_bad

    .line 1173
    new-instance v1, Lcom/classdojo/android/databinding/LayoutStudentConnectionsInitialBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutStudentConnectionsInitialBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1175
    :cond_bad
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_student_connections_initial is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2358
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_bc4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2359
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_bd3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2360
    :cond_bd3
    const-string v1, "layout/layout_progress_footer_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_be1

    .line 2361
    new-instance v1, Lcom/classdojo/android/databinding/LayoutProgressFooterBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutProgressFooterBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2363
    :cond_be1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_progress_footer is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1215
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_bf8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1216
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_c07

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1217
    :cond_c07
    const-string v1, "layout/layout_parent_connections_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_c15

    .line 1218
    new-instance v1, Lcom/classdojo/android/databinding/LayoutParentConnectionsListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutParentConnectionsListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1220
    :cond_c15
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_parent_connections_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 468
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_c2c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 469
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_c3b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 470
    :cond_c3b
    const-string v1, "layout/layout_parent_connections_invite_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_c49

    .line 471
    new-instance v1, Lcom/classdojo/android/databinding/LayoutParentConnectionsInviteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutParentConnectionsInviteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 473
    :cond_c49
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_parent_connections_invite is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2640
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_c60
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2641
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_c6f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2642
    :cond_c6f
    const-string v1, "layout/layout_parent_connections_initial_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_c7d

    .line 2643
    new-instance v1, Lcom/classdojo/android/databinding/LayoutParentConnectionsInitialBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutParentConnectionsInitialBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2645
    :cond_c7d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_parent_connections_initial is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2178
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_c94
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2179
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_ca3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2180
    :cond_ca3
    const-string v1, "layout/layout_legacy_video_view_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_cb1

    .line 2181
    new-instance v1, Lcom/classdojo/android/databinding/LayoutLegacyVideoViewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutLegacyVideoViewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2183
    :cond_cb1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_legacy_video_view is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 723
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_cc8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 724
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_cd7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 725
    :cond_cd7
    const-string v1, "layout/layout_dojo_video_view_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_ce5

    .line 726
    new-instance v1, Lcom/classdojo/android/databinding/LayoutDojoVideoViewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutDojoVideoViewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 728
    :cond_ce5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_dojo_video_view is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 603
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_cfc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 604
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_d0b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 605
    :cond_d0b
    const-string v1, "layout/layout_create_account_splash_page_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_d19

    .line 606
    new-instance v1, Lcom/classdojo/android/databinding/LayoutCreateAccountSplashPageBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/LayoutCreateAccountSplashPageBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 608
    :cond_d19
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for layout_create_account_splash_page is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1965
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_d30
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1966
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_d3f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1967
    :cond_d3f
    const-string v1, "layout/item_teacher_student_text_code_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_d4d

    .line 1968
    new-instance v1, Lcom/classdojo/android/databinding/ItemTeacherStudentTextCodeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemTeacherStudentTextCodeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1970
    :cond_d4d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_teacher_student_text_code is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2475
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_d64
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2476
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_d73

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2477
    :cond_d73
    const-string v1, "layout/item_parent_checklist_complete_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_d81

    .line 2478
    new-instance v1, Lcom/classdojo/android/databinding/ItemParentChecklistCompleteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemParentChecklistCompleteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2480
    :cond_d81
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_parent_checklist_complete is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1545
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_d98
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1546
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_da7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1547
    :cond_da7
    const-string v1, "layout/item_parent_checklist_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_db5

    .line 1548
    new-instance v1, Lcom/classdojo/android/databinding/ItemParentChecklistBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemParentChecklistBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1550
    :cond_db5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_parent_checklist is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 714
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_dcc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 715
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_ddb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 716
    :cond_ddb
    const-string v1, "layout/item_onboarding_class_code_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_de9

    .line 717
    new-instance v1, Lcom/classdojo/android/databinding/ItemOnboardingClassCodeHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemOnboardingClassCodeHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 719
    :cond_de9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_onboarding_class_code_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1197
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_e00
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1198
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_e0f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1199
    :cond_e0f
    const-string v1, "layout/item_grid_teacher_title_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_e1d

    .line 1200
    new-instance v1, Lcom/classdojo/android/databinding/ItemGridTeacherTitleBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemGridTeacherTitleBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1202
    :cond_e1d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_grid_teacher_title is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2106
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_e34
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2107
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_e43

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2108
    :cond_e43
    const-string v1, "layout/item_file_attachment_view_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_e51

    .line 2109
    new-instance v1, Lcom/classdojo/android/databinding/ItemFileAttachmentViewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemFileAttachmentViewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2111
    :cond_e51
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_file_attachment_view is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2502
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_e68
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2503
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_e77

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2504
    :cond_e77
    const-string v1, "layout/item_class_code_student_select_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_e85

    .line 2505
    new-instance v1, Lcom/classdojo/android/databinding/ItemClassCodeStudentSelectBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemClassCodeStudentSelectBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2507
    :cond_e85
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_class_code_student_select is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1770
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_e9c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1771
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_eab

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1772
    :cond_eab
    const-string v1, "layout/item_class_code_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_eb9

    .line 1773
    new-instance v1, Lcom/classdojo/android/databinding/ItemClassCodeHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ItemClassCodeHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1775
    :cond_eb9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for item_class_code_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1509
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_ed0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1510
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_edf

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1511
    :cond_edf
    const-string v1, "layout/invite_parent_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_eed

    .line 1512
    new-instance v1, Lcom/classdojo/android/databinding/InviteParentListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/InviteParentListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1514
    :cond_eed
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for invite_parent_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 450
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_f04
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 451
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_f13

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 452
    :cond_f13
    const-string v1, "layout/invite_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_f21

    .line 453
    new-instance v1, Lcom/classdojo/android/databinding/InviteListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/InviteListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 455
    :cond_f21
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for invite_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2241
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_f38
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2242
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_f47

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2243
    :cond_f47
    const-string v1, "layout/fragment_web_view_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_f55

    .line 2244
    new-instance v1, Lcom/classdojo/android/databinding/FragmentWebViewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentWebViewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2246
    :cond_f55
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_web_view is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2574
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_f6c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2575
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_f7b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2576
    :cond_f7b
    const-string v1, "layout/fragment_video_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_f89

    .line 2577
    new-instance v1, Lcom/classdojo/android/databinding/FragmentVideoPreviewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentVideoPreviewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2579
    :cond_f89
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_video_preview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2088
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_fa0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2089
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_faf

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2090
    :cond_faf
    const-string v1, "layout/fragment_teacher_welcome_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_fbd

    .line 2091
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherWelcomeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherWelcomeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2093
    :cond_fbd
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_welcome is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1098
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_fd4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1099
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_fe3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1100
    :cond_fe3
    const-string v1, "layout/fragment_teacher_student_connection_text_codes_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_ff1

    .line 1101
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherStudentConnectionTextCodesBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherStudentConnectionTextCodesBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1103
    :cond_ff1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_student_connection_text_codes is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2511
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1008
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2512
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1017

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2513
    :cond_1017
    const-string v1, "layout/fragment_teacher_student_connection_class_code_qr_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1025

    .line 2514
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherStudentConnectionClassCodeQrBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherStudentConnectionClassCodeQrBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2516
    :cond_1025
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_student_connection_class_code_qr is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1926
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_103c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1927
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_104b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1928
    :cond_104b
    const-string v1, "layout/fragment_teacher_story_feed_approval_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1059

    .line 1929
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherStoryFeedApprovalBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherStoryFeedApprovalBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1931
    :cond_1059
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_story_feed_approval is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2385
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1070
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2386
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_107f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2387
    :cond_107f
    const-string v1, "layout/fragment_teacher_settings_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_108d

    .line 2388
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherSettingsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherSettingsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2390
    :cond_108d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_settings is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1899
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_10a4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1900
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_10b3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1901
    :cond_10b3
    const-string v1, "layout/fragment_teacher_search_item_empty_teacher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_10c1

    .line 1902
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherSearchItemEmptyTeacherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherSearchItemEmptyTeacherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1904
    :cond_10c1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_search_item_empty_teacher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 504
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_10d8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 505
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_10e7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 506
    :cond_10e7
    const-string v1, "layout/fragment_teacher_connection_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_10f5

    .line 507
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherConnectionBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherConnectionBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 509
    :cond_10f5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_connection is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1260
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_110c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1261
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_111b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1262
    :cond_111b
    const-string v1, "layout/fragment_teacher_channel_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1129

    .line 1263
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherChannelListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherChannelListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1265
    :cond_1129
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_channel_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 843
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1140
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 844
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_114f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 845
    :cond_114f
    const-string v1, "layout/fragment_teacher_approval_feed_item_text_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_115d

    .line 846
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherApprovalFeedItemTextBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherApprovalFeedItemTextBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 848
    :cond_115d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_approval_feed_item_text is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2529
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1174
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2530
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1183

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2531
    :cond_1183
    const-string v1, "layout/fragment_teacher_approval_feed_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1191

    .line 2532
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTeacherApprovalFeedItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTeacherApprovalFeedItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2534
    :cond_1191
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_teacher_approval_feed_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2493
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_11a8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2494
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_11b7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2495
    :cond_11b7
    const-string v1, "layout/fragment_tab_student_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_11c5

    .line 2496
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabStudentListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabStudentListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2498
    :cond_11c5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_student_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1935
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_11dc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1936
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_11eb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1937
    :cond_11eb
    const-string v1, "layout-sw600dp-land/fragment_tab_story_feed_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_11f9

    .line 1938
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabStoryFeedBindingSw600dpLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabStoryFeedBindingSw600dpLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1940
    :cond_11f9
    const-string v1, "layout/fragment_tab_story_feed_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1207

    .line 1941
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabStoryFeedBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabStoryFeedBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1943
    :cond_1207
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_story_feed is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 93
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_121e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 94
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_122d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 95
    :cond_122d
    const-string v1, "layout/fragment_tab_notification_pending_posts_item_thumbnail_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_123b

    .line 96
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabNotificationPendingPostsItemThumbnailBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabNotificationPendingPostsItemThumbnailBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 98
    :cond_123b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_notification_pending_posts_item_thumbnail is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2421
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1252
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2422
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1261

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2423
    :cond_1261
    const-string v1, "layout/fragment_tab_notification_pending_posts_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_126f

    .line 2424
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabNotificationPendingPostsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabNotificationPendingPostsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2426
    :cond_126f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_notification_pending_posts_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1143
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1286
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1144
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1295

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1145
    :cond_1295
    const-string v1, "layout/fragment_tab_notification_list_pending_requests_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_12a3

    .line 1146
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabNotificationListPendingRequestsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabNotificationListPendingRequestsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1148
    :cond_12a3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_notification_list_pending_requests_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 750
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_12ba
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 751
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_12c9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 752
    :cond_12c9
    const-string v1, "layout/fragment_tab_notification_list_item_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_12d7

    .line 753
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabNotificationListItemHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabNotificationListItemHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 755
    :cond_12d7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_notification_list_item_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1455
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_12ee
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1456
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_12fd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1457
    :cond_12fd
    const-string v1, "layout/fragment_tab_notification_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_130b

    .line 1458
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabNotificationListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabNotificationListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1460
    :cond_130b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_notification_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1752
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1322
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1753
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1331

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1754
    :cond_1331
    const-string v1, "layout/fragment_tab_notification_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_133f

    .line 1755
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabNotificationListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabNotificationListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1757
    :cond_133f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_notification_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 153
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1356
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 154
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1365

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 155
    :cond_1365
    const-string v1, "layout/fragment_tab_class_wall_item_webview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1373

    .line 156
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemWebviewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemWebviewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 158
    :cond_1373
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_webview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1386
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_138a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1387
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1399

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1388
    :cond_1399
    const-string v1, "layout/fragment_tab_class_wall_item_student_report_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_13a7

    .line 1389
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemStudentReportBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemStudentReportBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1391
    :cond_13a7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_student_report is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1446
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_13be
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1447
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_13cd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1448
    :cond_13cd
    const-string v1, "layout/fragment_tab_class_wall_item_student_permission_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_13db

    .line 1449
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemStudentPermissionBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemStudentPermissionBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1451
    :cond_13db
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_student_permission is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1806
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_13f2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1807
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1401

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1808
    :cond_1401
    const-string v1, "layout/fragment_tab_class_wall_item_parent_empty_checklist_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_140f

    .line 1809
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemParentEmptyChecklistBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemParentEmptyChecklistBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1811
    :cond_140f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_parent_empty_checklist is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 888
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1426
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 889
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1435

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 890
    :cond_1435
    const-string v1, "layout/fragment_tab_class_wall_item_parent_empty_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1443

    .line 891
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemParentEmptyBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemParentEmptyBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 893
    :cond_1443
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_parent_empty is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 804
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_145a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 805
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1469

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 806
    :cond_1469
    const-string v1, "layout/fragment_tab_class_wall_item_invite_card_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1477

    .line 807
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemInviteCardBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemInviteCardBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 809
    :cond_1477
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_invite_card is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1251
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_148e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1252
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_149d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1253
    :cond_149d
    const-string v1, "layout/fragment_tab_class_wall_item_invite_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_14ab

    .line 1254
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemInviteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemInviteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1256
    :cond_14ab
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_invite is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2013
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_14c2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2014
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_14d1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2015
    :cond_14d1
    const-string v1, "layout/fragment_tab_class_wall_item_demo_empty_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_14df

    .line 2016
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemDemoEmptyBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemDemoEmptyBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2018
    :cond_14df
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_demo_empty is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 522
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_14f6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 523
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1505

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 524
    :cond_1505
    const-string v1, "layout/fragment_tab_class_wall_item_compose_large_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1513

    .line 525
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemComposeLargeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemComposeLargeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 527
    :cond_1513
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_compose_large is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 540
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_152a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 541
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1539

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 542
    :cond_1539
    const-string v1, "layout/fragment_tab_class_wall_item_compose_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1547

    .line 543
    new-instance v1, Lcom/classdojo/android/databinding/FragmentTabClassWallItemComposeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentTabClassWallItemComposeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 545
    :cond_1547
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_tab_class_wall_item_compose is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1689
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_155e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1690
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_156d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1691
    :cond_156d
    const-string v1, "layout/fragment_student_report_selector_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_157b

    .line 1692
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportSelectorItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportSelectorItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1694
    :cond_157b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report_selector_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1554
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1592
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1555
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_15a1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1556
    :cond_15a1
    const-string v1, "layout/fragment_student_report_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_15af

    .line 1557
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1559
    :cond_15af
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 405
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_15c6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 406
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_15d5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 407
    :cond_15d5
    const-string v1, "layout/fragment_student_report_list_dialog_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_15e3

    .line 408
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportListDialogBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportListDialogBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 410
    :cond_15e3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report_list_dialog is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 270
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_15fa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 271
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1609

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 272
    :cond_1609
    const-string v1, "layout/fragment_student_report_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1617

    .line 273
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 275
    :cond_1617
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 261
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_162e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 262
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_163d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 263
    :cond_163d
    const-string v1, "layout/fragment_student_report_charts_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_164b

    .line 264
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportChartsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportChartsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 266
    :cond_164b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report_charts_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 594
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1662
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 595
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1671

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 596
    :cond_1671
    const-string v1, "layout/fragment_student_report_add_note_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_167f

    .line 597
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportAddNoteItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportAddNoteItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 599
    :cond_167f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report_add_note_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 288
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1696
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 289
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_16a5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 290
    :cond_16a5
    const-string v1, "layout/fragment_student_report_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_16b3

    .line 291
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentReportBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentReportBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 293
    :cond_16b3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_report is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 324
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_16ca
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 325
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_16d9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 326
    :cond_16d9
    const-string v1, "layout/fragment_student_relation_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_16e7

    .line 327
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentRelationItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentRelationItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 329
    :cond_16e7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_relation_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1908
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_16fe
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1909
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_170d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1910
    :cond_170d
    const-string v1, "layout/fragment_student_registration_form_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_171b

    .line 1911
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentRegistrationFormBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentRegistrationFormBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1913
    :cond_171b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_registration_form is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1581
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1732
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1582
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1741

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1583
    :cond_1741
    const-string v1, "layout/fragment_student_profile_date_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_174f

    .line 1584
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentProfileDateBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentProfileDateBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1586
    :cond_174f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_profile_date is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2067
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1766
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2068
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1775

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2069
    :cond_1775
    const-string v1, "layout/fragment_student_login_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1783

    .line 2070
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentLoginListBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentLoginListBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2072
    :cond_1783
    const-string v1, "layout-sw600dp/fragment_student_login_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1791

    .line 2073
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentLoginListBindingSw600dpImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentLoginListBindingSw600dpImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2075
    :cond_1791
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_login_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 57
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_17a8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 58
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_17b7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 59
    :cond_17b7
    const-string v1, "layout/fragment_student_drawing_tool_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_17c5

    .line 60
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentDrawingToolBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentDrawingToolBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 62
    :cond_17c5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_drawing_tool is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 786
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_17dc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 787
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_17eb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 788
    :cond_17eb
    const-string v1, "layout/fragment_student_connections_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_17f9

    .line 789
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentConnectionsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentConnectionsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 791
    :cond_17f9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_connections is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2484
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1810
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2485
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_181f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2486
    :cond_181f
    const-string v1, "layout/fragment_student_codes_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_182d

    .line 2487
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCodesItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCodesItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2489
    :cond_182d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_codes_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1707
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1844
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1708
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1853

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1709
    :cond_1853
    const-string v1, "layout/fragment_student_codes_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1861

    .line 1710
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCodesBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCodesBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1712
    :cond_1861
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_codes is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 684
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1878
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 685
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1887

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 686
    :cond_1887
    const-string v1, "layout/fragment_student_code_form_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1895

    .line 687
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCodeFormBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCodeFormBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 689
    :cond_1895
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_code_form is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 459
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_18ac
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 460
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_18bb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 461
    :cond_18bb
    const-string v1, "layout/fragment_student_capture_student_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_18c9

    .line 462
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureStudentListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureStudentListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 464
    :cond_18c9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_student_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1332
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_18e0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1333
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_18ef

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1334
    :cond_18ef
    const-string v1, "layout/fragment_student_capture_student_list_home_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_18fd

    .line 1335
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureStudentListHomeItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureStudentListHomeItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1337
    :cond_18fd
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_student_list_home_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2631
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1914
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2632
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1923

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2633
    :cond_1923
    const-string v1, "layout/fragment_student_capture_story_feed_item_participant_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1931

    .line 2634
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemParticipantBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemParticipantBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2636
    :cond_1931
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_story_feed_item_participant is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1188
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1948
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1189
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1957

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1190
    :cond_1957
    const-string v1, "layout/fragment_student_capture_story_feed_item_content_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1965

    .line 1191
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemContentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemContentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1193
    :cond_1965
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_story_feed_item_content is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 693
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_197c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 694
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_198b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 695
    :cond_198b
    const-string v1, "layout-sw600dp-land/fragment_student_capture_story_feed_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1999

    .line 696
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemBindingSw600dpLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemBindingSw600dpLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 698
    :cond_1999
    const-string v1, "layout/fragment_student_capture_story_feed_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_19a7

    .line 699
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureStoryFeedItemBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 701
    :cond_19a7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_story_feed_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 120
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_19be
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 121
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_19cd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 122
    :cond_19cd
    const-string v1, "layout-sw600dp/fragment_student_capture_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_19db

    .line 123
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCapturePreviewBindingSw600dpImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCapturePreviewBindingSw600dpImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 125
    :cond_19db
    const-string v1, "layout/fragment_student_capture_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_19e9

    .line 126
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCapturePreviewBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCapturePreviewBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 128
    :cond_19e9
    const-string v1, "layout-sw600dp-land/fragment_student_capture_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_19f7

    .line 129
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCapturePreviewBindingSw600dpLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCapturePreviewBindingSw600dpLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 131
    :cond_19f7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_preview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 423
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1a0e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 424
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1a1d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 425
    :cond_1a1d
    const-string v1, "layout/fragment_student_capture_mark_students_marked_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1a2b

    .line 426
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureMarkStudentsMarkedItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureMarkStudentsMarkedItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 428
    :cond_1a2b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_mark_students_marked_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 297
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1a42
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 298
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1a51

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 299
    :cond_1a51
    const-string v1, "layout/fragment_student_capture_mark_students_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1a5f

    .line 300
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureMarkStudentsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureMarkStudentsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 302
    :cond_1a5f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_mark_students_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1671
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1a76
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1672
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1a85

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1673
    :cond_1a85
    const-string v1, "layout/fragment_student_capture_home_student_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1a93

    .line 1674
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureHomeStudentListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureHomeStudentListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1676
    :cond_1a93
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_home_student_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2601
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1aaa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2602
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1ab9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2603
    :cond_1ab9
    const-string v1, "layout/fragment_student_capture_home_story_feed_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1ac7

    .line 2604
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentCaptureHomeStoryFeedBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentCaptureHomeStoryFeedBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2606
    :cond_1ac7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_capture_home_story_feed is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1854
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1ade
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1855
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1aed

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1856
    :cond_1aed
    const-string v1, "layout/fragment_student_avatar_editor_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1afb

    .line 1857
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStudentAvatarEditorBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStudentAvatarEditorBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1859
    :cond_1afb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_student_avatar_editor is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1323
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1b12
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1324
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1b21

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1325
    :cond_1b21
    const-string v1, "layout/fragment_story_share_to_student_header_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1b2f

    .line 1326
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStoryShareToStudentHeaderItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStoryShareToStudentHeaderItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1328
    :cond_1b2f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_story_share_to_student_header_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 252
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1b46
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 253
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1b55

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 254
    :cond_1b55
    const-string v1, "layout/fragment_story_share_to_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1b63

    .line 255
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStoryShareToItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStoryShareToItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 257
    :cond_1b63
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_story_share_to_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1563
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1b7a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1564
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1b89

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1565
    :cond_1b89
    const-string v1, "layout/fragment_story_share_to_class_header_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1b97

    .line 1566
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStoryShareToClassHeaderItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStoryShareToClassHeaderItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1568
    :cond_1b97
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_story_share_to_class_header_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1491
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1bae
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1492
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1bbd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1493
    :cond_1bbd
    const-string v1, "layout/fragment_story_share_to_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1bcb

    .line 1494
    new-instance v1, Lcom/classdojo/android/databinding/FragmentStoryShareToBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentStoryShareToBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1496
    :cond_1bcb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_story_share_to is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 576
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1be2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 577
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1bf1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 578
    :cond_1bf1
    const-string v1, "layout/fragment_single_notification_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1bff

    .line 579
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSingleNotificationBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSingleNotificationBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 581
    :cond_1bff
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_single_notification is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1035
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1c16
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1036
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1c25

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1037
    :cond_1c25
    const-string v1, "layout/fragment_setup_skills_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1c33

    .line 1038
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSetupSkillsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSetupSkillsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1040
    :cond_1c33
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_setup_skills is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 432
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1c4a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 433
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1c59

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 434
    :cond_1c59
    const-string v1, "layout/fragment_seen_by_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1c67

    .line 435
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSeenByItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSeenByItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 437
    :cond_1c67
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_seen_by_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1014
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1c7e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1015
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1c8d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1016
    :cond_1c8d
    const-string v1, "layout/fragment_seen_by_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1c9b

    .line 1017
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSeenByBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSeenByBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1019
    :cond_1c9b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_seen_by is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2196
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1cb2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2197
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1cc1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2198
    :cond_1cc1
    const-string v1, "layout/fragment_school_search_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1ccf

    .line 2199
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolSearchItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolSearchItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2201
    :cond_1ccf
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_search_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2376
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1ce6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2377
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1cf5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2378
    :cond_1cf5
    const-string v1, "layout/fragment_school_search_adapter_footer_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1d03

    .line 2379
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolSearchAdapterFooterBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolSearchAdapterFooterBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2381
    :cond_1d03
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_search_adapter_footer is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 144
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1d1a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 145
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1d29

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 146
    :cond_1d29
    const-string v1, "layout/fragment_school_search_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1d37

    .line 147
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolSearchBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolSearchBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 149
    :cond_1d37
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_search is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 414
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1d4e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 415
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1d5d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 416
    :cond_1d5d
    const-string v1, "layout/fragment_school_null_state_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1d6b

    .line 417
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolNullStateBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolNullStateBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 419
    :cond_1d6b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_null_state is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 198
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1d82
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 199
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1d91

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 200
    :cond_1d91
    const-string v1, "layout/fragment_school_directory_item_welcome_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1d9f

    .line 201
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemWelcomeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemWelcomeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 203
    :cond_1d9f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_welcome is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1734
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1db6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1735
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1dc5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1736
    :cond_1dc5
    const-string v1, "layout/fragment_school_directory_item_teacher_request_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1dd3

    .line 1737
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemTeacherRequestBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemTeacherRequestBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1739
    :cond_1dd3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_teacher_request is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 378
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1dea
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 379
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1df9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 380
    :cond_1df9
    const-string v1, "layout/fragment_school_directory_item_teacher_pending_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1e07

    .line 381
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemTeacherPendingBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemTeacherPendingBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 383
    :cond_1e07
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_teacher_pending is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2331
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1e1e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2332
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1e2d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2333
    :cond_1e2d
    const-string v1, "layout/fragment_school_directory_item_teacher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1e3b

    .line 2334
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemTeacherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemTeacherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2336
    :cond_1e3b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_teacher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2448
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1e52
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2449
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1e61

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2450
    :cond_1e61
    const-string v1, "layout/fragment_school_directory_item_student_parent_circle_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1e6f

    .line 2451
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemStudentParentCircleBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemStudentParentCircleBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2453
    :cond_1e6f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_student_parent_circle is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1152
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1e86
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1153
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1e95

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1154
    :cond_1e95
    const-string v1, "layout/fragment_school_directory_item_student_add_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1ea3

    .line 1155
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemStudentAddBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemStudentAddBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1157
    :cond_1ea3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_student_add is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2313
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1eba
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2314
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1ec9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2315
    :cond_1ec9
    const-string v1, "layout/fragment_school_directory_item_student_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1ed7

    .line 2316
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemStudentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemStudentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2318
    :cond_1ed7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_student is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1716
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1eee
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1717
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1efd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1718
    :cond_1efd
    const-string v1, "layout/fragment_school_directory_item_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1f0b

    .line 1719
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryItemHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1721
    :cond_1f0b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory_item_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2622
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1f22
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2623
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1f31

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2624
    :cond_1f31
    const-string v1, "layout/fragment_school_directory_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1f3f

    .line 2625
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDirectoryBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2627
    :cond_1f3f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_directory is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 558
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1f56
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 559
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1f65

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 560
    :cond_1f65
    const-string v1, "layout/fragment_school_detail_teacher_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1f73

    .line 561
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDetailTeacherItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDetailTeacherItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 563
    :cond_1f73
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_detail_teacher_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1134
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1f8a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1135
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1f99

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1136
    :cond_1f99
    const-string v1, "layout/fragment_school_detail_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1fa7

    .line 1137
    new-instance v1, Lcom/classdojo/android/databinding/FragmentSchoolDetailHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentSchoolDetailHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1139
    :cond_1fa7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_school_detail_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2142
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1fbe
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2143
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_1fcd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2144
    :cond_1fcd
    const-string v1, "layout/fragment_scheduled_messages_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_1fdb

    .line 2145
    new-instance v1, Lcom/classdojo/android/databinding/FragmentScheduledMessagesBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentScheduledMessagesBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2147
    :cond_1fdb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_scheduled_messages is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2367
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_1ff2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2368
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2001

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2369
    :cond_2001
    const-string v1, "layout/fragment_scheduled_message_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_200f

    .line 2370
    new-instance v1, Lcom/classdojo/android/databinding/FragmentScheduledMessageItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentScheduledMessageItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2372
    :cond_200f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_scheduled_message_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2268
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2026
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2269
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2035

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2270
    :cond_2035
    const-string v1, "layout/fragment_push_notifications_settings_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2043

    .line 2271
    new-instance v1, Lcom/classdojo/android/databinding/FragmentPushNotificationsSettingsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentPushNotificationsSettingsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2273
    :cond_2043
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_push_notifications_settings is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2439
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_205a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2440
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2069

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2441
    :cond_2069
    const-string v1, "layout/fragment_push_notifications_quiet_hours_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2077

    .line 2442
    new-instance v1, Lcom/classdojo/android/databinding/FragmentPushNotificationsQuietHoursBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentPushNotificationsQuietHoursBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2444
    :cond_2077
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_push_notifications_quiet_hours is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 879
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_208e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 880
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_209d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 881
    :cond_209d
    const-string v1, "layout/fragment_profile_photo_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_20ab

    .line 882
    new-instance v1, Lcom/classdojo/android/databinding/FragmentProfilePhotoItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentProfilePhotoItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 884
    :cond_20ab
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_profile_photo_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 861
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_20c2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 862
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_20d1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 863
    :cond_20d1
    const-string v1, "layout/fragment_preview_message_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_20df

    .line 864
    new-instance v1, Lcom/classdojo/android/databinding/FragmentPreviewMessageBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentPreviewMessageBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 866
    :cond_20df
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_preview_message is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 102
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_20f6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 103
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2105

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 104
    :cond_2105
    const-string v1, "layout/fragment_photo_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2113

    .line 105
    new-instance v1, Lcom/classdojo/android/databinding/FragmentPhotoPreviewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentPhotoPreviewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 107
    :cond_2113
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_photo_preview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 162
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_212a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 163
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2139

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 164
    :cond_2139
    const-string v1, "layout/fragment_parent_teacher_search_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2147

    .line 165
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentTeacherSearchBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentTeacherSearchBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 167
    :cond_2147
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_teacher_search is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 675
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_215e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 676
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_216d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 677
    :cond_216d
    const-string v1, "layout/fragment_parent_sign_up_credentials_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_217b

    .line 678
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentSignUpCredentialsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentSignUpCredentialsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 680
    :cond_217b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_sign_up_credentials is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2058
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2192
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2059
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_21a1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2060
    :cond_21a1
    const-string v1, "layout/fragment_parent_setup_student_account_qr_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_21af

    .line 2061
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentSetupStudentAccountQrBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentSetupStudentAccountQrBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2063
    :cond_21af
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_setup_student_account_qr is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2583
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_21c6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2584
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_21d5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2585
    :cond_21d5
    const-string v1, "layout/fragment_parent_setup_student_account_ack_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_21e3

    .line 2586
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentSetupStudentAccountAckBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentSetupStudentAccountAckBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2588
    :cond_21e3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_setup_student_account_ack is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1482
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_21fa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1483
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2209

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1484
    :cond_2209
    const-string v1, "layout/fragment_parent_school_search_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2217

    .line 1485
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentSchoolSearchItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentSchoolSearchItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1487
    :cond_2217
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_school_search_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 852
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_222e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 853
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_223d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 854
    :cond_223d
    const-string v1, "layout/fragment_parent_school_search_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_224b

    .line 855
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentSchoolSearchBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentSchoolSearchBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 857
    :cond_224b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_school_search is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2340
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2262
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2341
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2271

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2342
    :cond_2271
    const-string v1, "layout/fragment_parent_connections_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_227f

    .line 2343
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentConnectionsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentConnectionsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2345
    :cond_227f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_connections is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1269
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2296
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1270
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_22a5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1271
    :cond_22a5
    const-string v1, "layout/fragment_parent_connection_request_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_22b3

    .line 1272
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentConnectionRequestBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentConnectionRequestBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1274
    :cond_22b3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_connection_request is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1053
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_22ca
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1054
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_22d9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1055
    :cond_22d9
    const-string v1, "layout/fragment_parent_checklist_invite_family_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_22e7

    .line 1056
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentChecklistInviteFamilyItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentChecklistInviteFamilyItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1058
    :cond_22e7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_checklist_invite_family_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 531
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_22fe
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 532
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_230d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 533
    :cond_230d
    const-string v1, "layout/fragment_parent_checklist_birthday_capture_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_231b

    .line 534
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentChecklistBirthdayCaptureItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentChecklistBirthdayCaptureItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 536
    :cond_231b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_checklist_birthday_capture_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2169
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2332
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2170
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2341

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2171
    :cond_2341
    const-string v1, "layout/fragment_parent_channel_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_234f

    .line 2172
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentChannelListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentChannelListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2174
    :cond_234f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_channel_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1437
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2366
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1438
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2375

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1439
    :cond_2375
    const-string v1, "layout/fragment_parent_add_class_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2383

    .line 1440
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentAddClassBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentAddClassBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1442
    :cond_2383
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_add_class is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2079
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_239a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2080
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_23a9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2081
    :cond_23a9
    const-string v1, "layout/fragment_parent_account_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_23b7

    .line 2082
    new-instance v1, Lcom/classdojo/android/databinding/FragmentParentAccountBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentParentAccountBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2084
    :cond_23b7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_parent_account is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2457
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_23ce
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2458
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_23dd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2459
    :cond_23dd
    const-string v1, "layout/fragment_onboarding_student_avatar_editor_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_23eb

    .line 2460
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingStudentAvatarEditorBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingStudentAvatarEditorBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2462
    :cond_23eb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_student_avatar_editor is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 342
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2402
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 343
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2411

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 344
    :cond_2411
    const-string v1, "layout/fragment_onboarding_splash_user_role_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_241f

    .line 345
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingSplashUserRoleBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingSplashUserRoleBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 347
    :cond_241f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_splash_user_role is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2115
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2436
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2116
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2445

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2117
    :cond_2445
    const-string v1, "layout/fragment_onboarding_sign_up_title_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2453

    .line 2118
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpTitleBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpTitleBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2120
    :cond_2453
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_sign_up_title is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1464
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_246a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1465
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2479

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1466
    :cond_2479
    const-string v1, "layout/fragment_onboarding_sign_up_parent_anti_abuse_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2487

    .line 1467
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpParentAntiAbuseBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpParentAntiAbuseBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1469
    :cond_2487
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_sign_up_parent_anti_abuse is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 759
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_249e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 760
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_24ad

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 761
    :cond_24ad
    const-string v1, "layout/fragment_onboarding_sign_up_email_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_24bb

    .line 762
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpEmailBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpEmailBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 764
    :cond_24bb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_sign_up_email is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 111
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_24d2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 112
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_24e1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 113
    :cond_24e1
    const-string v1, "layout/fragment_onboarding_sign_up_details_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_24ef

    .line 114
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpDetailsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingSignUpDetailsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 116
    :cond_24ef
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_sign_up_details is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 477
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2506
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 478
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2515

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 479
    :cond_2515
    const-string v1, "layout/fragment_onboarding_enter_code_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2523

    .line 480
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingEnterCodeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingEnterCodeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 482
    :cond_2523
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_enter_code is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1608
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_253a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1609
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2549

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1610
    :cond_2549
    const-string v1, "layout/fragment_onboarding_enable_camera_primer_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2557

    .line 1611
    new-instance v1, Lcom/classdojo/android/databinding/FragmentOnboardingEnableCameraPrimerBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentOnboardingEnableCameraPrimerBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1613
    :cond_2557
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_onboarding_enable_camera_primer is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 612
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_256e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 613
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_257d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 614
    :cond_257d
    const-string v1, "layout/fragment_message_recipients_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_258b

    .line 615
    new-instance v1, Lcom/classdojo/android/databinding/FragmentMessageRecipientsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentMessageRecipientsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 617
    :cond_258b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_message_recipients is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2205
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_25a2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2206
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_25b1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2207
    :cond_25b1
    const-string v1, "layout/fragment_meet_teacher_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_25bf

    .line 2208
    new-instance v1, Lcom/classdojo/android/databinding/FragmentMeetTeacherItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentMeetTeacherItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2210
    :cond_25bf
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_meet_teacher_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1023
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_25d6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1024
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_25e5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1025
    :cond_25e5
    const-string v1, "layout-sw600dp/fragment_mark_students_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_25f3

    .line 1026
    new-instance v1, Lcom/classdojo/android/databinding/FragmentMarkStudentsBindingSw600dpImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentMarkStudentsBindingSw600dpImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1028
    :cond_25f3
    const-string v1, "layout/fragment_mark_students_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2601

    .line 1029
    new-instance v1, Lcom/classdojo/android/databinding/FragmentMarkStudentsBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentMarkStudentsBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1031
    :cond_2601
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_mark_students is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 189
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2618
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 190
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2627

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 191
    :cond_2627
    const-string v1, "layout/fragment_login_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2635

    .line 192
    new-instance v1, Lcom/classdojo/android/databinding/FragmentLoginBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentLoginBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 194
    :cond_2635
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_login is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2277
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_264c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2278
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_265b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2279
    :cond_265b
    const-string v1, "layout/fragment_leader_sign_up_role_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2669

    .line 2280
    new-instance v1, Lcom/classdojo/android/databinding/FragmentLeaderSignUpRoleBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentLeaderSignUpRoleBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2282
    :cond_2669
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_leader_sign_up_role is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1626
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2680
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1627
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_268f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1628
    :cond_268f
    const-string v1, "layout/fragment_kids_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_269d

    .line 1629
    new-instance v1, Lcom/classdojo/android/databinding/FragmentKidsListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentKidsListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1631
    :cond_269d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_kids_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2232
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_26b4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2233
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_26c3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2234
    :cond_26c3
    const-string v1, "layout/fragment_invite_item_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_26d1

    .line 2235
    new-instance v1, Lcom/classdojo/android/databinding/FragmentInviteItemHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentInviteItemHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2237
    :cond_26d1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_invite_item_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 897
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_26e8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 898
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_26f7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 899
    :cond_26f7
    const-string v1, "layout/fragment_hold_tight_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2705

    .line 900
    new-instance v1, Lcom/classdojo/android/databinding/FragmentHoldTightItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentHoldTightItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 902
    :cond_2705
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_hold_tight_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2403
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_271c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2404
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_272b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2405
    :cond_272b
    const-string v1, "layout/fragment_forgot_password_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2739

    .line 2406
    new-instance v1, Lcom/classdojo/android/databinding/FragmentForgotPasswordBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentForgotPasswordBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2408
    :cond_2739
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_forgot_password is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2565
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2750
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2566
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_275f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2567
    :cond_275f
    const-string v1, "layout/fragment_enter_student_mode_qr_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_276d

    .line 2568
    new-instance v1, Lcom/classdojo/android/databinding/FragmentEnterStudentModeQrBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentEnterStudentModeQrBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2570
    :cond_276d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_enter_student_mode_qr is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1107
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2784
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1108
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2793

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1109
    :cond_2793
    const-string v1, "layout/fragment_edit_students_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_27a1

    .line 1110
    new-instance v1, Lcom/classdojo/android/databinding/FragmentEditStudentsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentEditStudentsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1112
    :cond_27a1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_edit_students_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1725
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_27b8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1726
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_27c7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1727
    :cond_27c7
    const-string v1, "layout/fragment_edit_students_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_27d5

    .line 1728
    new-instance v1, Lcom/classdojo/android/databinding/FragmentEditStudentsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentEditStudentsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1730
    :cond_27d5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_edit_students is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1350
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_27ec
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1351
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_27fb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1352
    :cond_27fb
    const-string v1, "layout/fragment_edit_behaviours_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2809

    .line 1353
    new-instance v1, Lcom/classdojo/android/databinding/FragmentEditBehavioursBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentEditBehavioursBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1355
    :cond_2809
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_edit_behaviours is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1089
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2820
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1090
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_282f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1091
    :cond_282f
    const-string v1, "layout/fragment_dojo_photo_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_283d

    .line 1092
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoPhotoPreviewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoPhotoPreviewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1094
    :cond_283d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_photo_preview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 171
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2854
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 172
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2863

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 173
    :cond_2863
    const-string v1, "layout/fragment_dojo_camera_tooltip_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2871

    .line 174
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraTooltipBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraTooltipBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 176
    :cond_2871
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_camera_tooltip is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 360
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2888
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 361
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2897

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 362
    :cond_2897
    const-string v1, "layout-sw600dp/fragment_dojo_camera_controls_90_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_28a5

    .line 363
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraControls90Binding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraControls90Binding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 365
    :cond_28a5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_camera_controls_90 is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1179
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_28bc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1180
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_28cb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1181
    :cond_28cb
    const-string v1, "layout-sw600dp/fragment_dojo_camera_controls_270_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_28d9

    .line 1182
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraControls270Binding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraControls270Binding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1184
    :cond_28d9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_camera_controls_270 is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1956
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_28f0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1957
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_28ff

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1958
    :cond_28ff
    const-string v1, "layout-sw600dp/fragment_dojo_camera_controls_180_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_290d

    .line 1959
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraControls180Binding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraControls180Binding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1961
    :cond_290d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_camera_controls_180 is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2001
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2924
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2002
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2933

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2003
    :cond_2933
    const-string v1, "layout-sw600dp/fragment_dojo_camera_controls_0_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2941

    .line 2004
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraControls0BindingSw600dpImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraControls0BindingSw600dpImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2006
    :cond_2941
    const-string v1, "layout/fragment_dojo_camera_controls_0_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_294f

    .line 2007
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraControls0BindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraControls0BindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2009
    :cond_294f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_camera_controls_0 is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2040
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2966
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2041
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2975

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2042
    :cond_2975
    const-string v1, "layout/fragment_dojo_camera_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2983

    .line 2043
    new-instance v1, Lcom/classdojo/android/databinding/FragmentDojoCameraBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentDojoCameraBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2045
    :cond_2983
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_dojo_camera is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2160
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_299a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2161
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_29a9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2162
    :cond_29a9
    const-string v1, "layout/fragment_create_school_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_29b7

    .line 2163
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCreateSchoolBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCreateSchoolBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2165
    :cond_29b7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_create_school is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1992
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_29ce
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1993
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_29dd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1994
    :cond_29dd
    const-string v1, "layout/fragment_create_account_splash_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_29eb

    .line 1995
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCreateAccountSplashBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCreateAccountSplashBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1997
    :cond_29eb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_create_account_splash is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 39
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2a02
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 40
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2a11

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 41
    :cond_2a11
    const-string v1, "layout/fragment_combined_fullscreen_photo_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2a1f

    .line 42
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCombinedFullscreenPhotoPreviewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCombinedFullscreenPhotoPreviewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 44
    :cond_2a1f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_combined_fullscreen_photo_preview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 870
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2a36
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 871
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2a45

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 872
    :cond_2a45
    const-string v1, "layout/fragment_combined_drawing_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2a53

    .line 873
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCombinedDrawingBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCombinedDrawingBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 875
    :cond_2a53
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_combined_drawing is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1590
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2a6a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1591
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2a79

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1592
    :cond_2a79
    const-string v1, "layout/fragment_combined_camera_preview_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2a87

    .line 1593
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCombinedCameraPreviewBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCombinedCameraPreviewBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1595
    :cond_2a87
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_combined_camera_preview is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 732
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2a9e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 733
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2aad

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 734
    :cond_2aad
    const-string v1, "layout/fragment_combined_camera_controls_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2abb

    .line 735
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCombinedCameraControlsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCombinedCameraControlsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 737
    :cond_2abb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_combined_camera_controls is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 933
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2ad2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 934
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ae1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 935
    :cond_2ae1
    const-string v1, "layout/fragment_combined_camera_compose_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2aef

    .line 936
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCombinedCameraComposeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCombinedCameraComposeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 938
    :cond_2aef
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_combined_camera_compose is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1761
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2b06
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1762
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2b15

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1763
    :cond_2b15
    const-string v1, "layout/fragment_combined_camera_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2b23

    .line 1764
    new-instance v1, Lcom/classdojo/android/databinding/FragmentCombinedCameraBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentCombinedCameraBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1766
    :cond_2b23
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_combined_camera is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 66
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2b3a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 67
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2b49

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 68
    :cond_2b49
    const-string v1, "layout/fragment_classroom_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2b57

    .line 69
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassroomBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassroomBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 71
    :cond_2b57
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_classroom is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 21
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2b6e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 22
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2b7d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 23
    :cond_2b7d
    const-string v1, "layout/fragment_class_wall_settings_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2b8b

    .line 24
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassWallSettingsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassWallSettingsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 26
    :cond_2b8b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_wall_settings is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 666
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2ba2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 667
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2bb1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 668
    :cond_2bb1
    const-string v1, "layout/fragment_class_wall_compose_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2bbf

    .line 669
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassWallComposeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassWallComposeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 671
    :cond_2bbf
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_wall_compose is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1404
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2bd6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1405
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2be5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1406
    :cond_2be5
    const-string v1, "layout/fragment_class_list_item_teacher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2bf3

    .line 1407
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassListItemTeacherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassListItemTeacherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1409
    :cond_2bf3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_list_item_teacher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1653
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2c0a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1654
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2c19

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1655
    :cond_2c19
    const-string v1, "layout/fragment_class_list_item_school_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2c27

    .line 1656
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassListItemSchoolBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassListItemSchoolBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1658
    :cond_2c27
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_list_item_school is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 135
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2c3e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 136
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2c4d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 137
    :cond_2c4d
    const-string v1, "layout/fragment_class_list_item_class_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2c5b

    .line 138
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassListItemClassBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassListItemClassBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 140
    :cond_2c5b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_list_item_class is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1947
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2c72
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1948
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2c81

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1949
    :cond_2c81
    const-string v1, "layout/fragment_class_list_item_add_class_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2c8f

    .line 1950
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassListItemAddClassBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassListItemAddClassBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1952
    :cond_2c8f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_list_item_add_class is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 234
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2ca6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 235
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2cb5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 236
    :cond_2cb5
    const-string v1, "layout/fragment_class_code_student_select_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2cc3

    .line 237
    new-instance v1, Lcom/classdojo/android/databinding/FragmentClassCodeStudentSelectBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentClassCodeStudentSelectBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 239
    :cond_2cc3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_class_code_student_select is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2547
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2cda
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2548
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ce9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2549
    :cond_2ce9
    const-string v1, "layout/fragment_chat_item_sticker_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2cf7

    .line 2550
    new-instance v1, Lcom/classdojo/android/databinding/FragmentChatItemStickerBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentChatItemStickerBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2552
    :cond_2cf7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_chat_item_sticker is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1080
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2d0e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1081
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2d1d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1082
    :cond_2d1d
    const-string v1, "layout/fragment_chat_empty_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2d2b

    .line 1083
    new-instance v1, Lcom/classdojo/android/databinding/FragmentChatEmptyBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentChatEmptyBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1085
    :cond_2d2b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_chat_empty is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1536
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2d42
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1537
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2d51

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1538
    :cond_2d51
    const-string v1, "layout/fragment_chat_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2d5f

    .line 1539
    new-instance v1, Lcom/classdojo/android/databinding/FragmentChatBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentChatBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1541
    :cond_2d5f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_chat is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1278
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2d76
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1279
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2d85

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1280
    :cond_2d85
    const-string v1, "layout/fragment_change_password_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2d93

    .line 1281
    new-instance v1, Lcom/classdojo/android/databinding/FragmentChangePasswordBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentChangePasswordBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1283
    :cond_2d93
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_change_password is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2394
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2daa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2395
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2db9

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2396
    :cond_2db9
    const-string v1, "layout/fragment_award_pager_dialog_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2dc7

    .line 2397
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAwardPagerDialogBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAwardPagerDialogBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2399
    :cond_2dc7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_award_pager_dialog is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 969
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2dde
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 970
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ded

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 971
    :cond_2ded
    const-string v1, "layout/fragment_award_grid_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2dfb

    .line 972
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAwardGridItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAwardGridItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 974
    :cond_2dfb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_award_grid_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1662
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2e12
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1663
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2e21

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1664
    :cond_2e21
    const-string v1, "layout/fragment_award_grid_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2e2f

    .line 1665
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAwardGridBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAwardGridBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1667
    :cond_2e2f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_award_grid is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1500
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2e46
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1501
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2e55

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1502
    :cond_2e55
    const-string v1, "layout/fragment_audience_selector_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2e63

    .line 1503
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAudienceSelectorItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAudienceSelectorItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1505
    :cond_2e63
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_audience_selector_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2430
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2e7a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2431
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2e89

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2432
    :cond_2e89
    const-string v1, "layout/fragment_attendance_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2e97

    .line 2433
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAttendanceItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAttendanceItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2435
    :cond_2e97
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_attendance_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 996
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2eae
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 997
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ebd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 998
    :cond_2ebd
    const-string v1, "layout/fragment_attendance_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2ecb

    .line 999
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAttendanceBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAttendanceBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1001
    :cond_2ecb
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_attendance is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 75
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2ee2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 76
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ef1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 77
    :cond_2ef1
    const-string v1, "layout/fragment_add_school_student_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2eff

    .line 78
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddSchoolStudentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddSchoolStudentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 80
    :cond_2eff
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_school_student is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2295
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2f16
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2296
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2f25

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2297
    :cond_2f25
    const-string v1, "layout/fragment_add_edit_student_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2f33

    .line 2298
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddEditStudentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddEditStudentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2300
    :cond_2f33
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_edit_student is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2556
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2f4a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2557
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2f59

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2558
    :cond_2f59
    const-string v1, "layout/fragment_add_edit_group_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2f67

    .line 2559
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddEditGroupBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddEditGroupBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2561
    :cond_2f67
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_edit_group is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1005
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2f7e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1006
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2f8d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1007
    :cond_2f8d
    const-string v1, "layout/fragment_add_edit_class_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2f9b

    .line 1008
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddEditClassBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddEditClassBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1010
    :cond_2f9b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_edit_class is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 243
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2fb2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 244
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2fc1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 245
    :cond_2fc1
    const-string v1, "layout/fragment_add_edit_behaviours_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2fcf

    .line 246
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddEditBehavioursBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddEditBehavioursBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 248
    :cond_2fcf
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_edit_behaviours is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 834
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_2fe6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 835
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_2ff5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 836
    :cond_2ff5
    const-string v1, "layout/fragment_add_coteacher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3003

    .line 837
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddCoteacherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddCoteacherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 839
    :cond_3003
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_coteacher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1413
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_301a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1414
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3029

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1415
    :cond_3029
    const-string v1, "layout-land/fragment_add_class_poster_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3037

    .line 1416
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddClassPosterBindingLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddClassPosterBindingLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1418
    :cond_3037
    const-string v1, "layout/fragment_add_class_poster_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3045

    .line 1419
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddClassPosterBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddClassPosterBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1421
    :cond_3045
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_class_poster is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1116
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_305c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1117
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_306b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1118
    :cond_306b
    const-string v1, "layout/fragment_add_class_invite_code_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3079

    .line 1119
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddClassInviteCodeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddClassInviteCodeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1121
    :cond_3079
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_class_invite_code is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1983
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3090
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1984
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_309f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1985
    :cond_309f
    const-string v1, "layout/fragment_add_class_invite_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_30ad

    .line 1986
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAddClassInviteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAddClassInviteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1988
    :cond_30ad
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_add_class_invite is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2592
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_30c4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2593
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_30d3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2594
    :cond_30d3
    const-string v1, "layout/fragment_account_switcher_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_30e1

    .line 2595
    new-instance v1, Lcom/classdojo/android/databinding/FragmentAccountSwitcherItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/FragmentAccountSwitcherItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2597
    :cond_30e1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for fragment_account_switcher_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2031
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_30f8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2032
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3107

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2033
    :cond_3107
    const-string v1, "layout/dialog_unsupported_class_code_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3115

    .line 2034
    new-instance v1, Lcom/classdojo/android/databinding/DialogUnsupportedClassCodeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogUnsupportedClassCodeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2036
    :cond_3115
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_unsupported_class_code is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 387
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_312c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 388
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_313b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 389
    :cond_313b
    const-string v1, "layout/dialog_turn_on_password_lock_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3149

    .line 390
    new-instance v1, Lcom/classdojo/android/databinding/DialogTurnOnPasswordLockBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogTurnOnPasswordLockBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 392
    :cond_3149
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_turn_on_password_lock is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 513
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3160
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 514
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_316f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 515
    :cond_316f
    const-string v1, "layout/dialog_teacher_student_consent_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_317d

    .line 516
    new-instance v1, Lcom/classdojo/android/databinding/DialogTeacherStudentConsentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogTeacherStudentConsentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 518
    :cond_317d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_teacher_student_consent is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1341
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3194
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1342
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_31a3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1343
    :cond_31a3
    const-string v1, "layout/dialog_teacher_new_class_success_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_31b1

    .line 1344
    new-instance v1, Lcom/classdojo/android/databinding/DialogTeacherNewClassSuccessBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogTeacherNewClassSuccessBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1346
    :cond_31b1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_teacher_new_class_success is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2322
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_31c8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2323
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_31d7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2324
    :cond_31d7
    const-string v1, "layout/dialog_teacher_invitation_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_31e5

    .line 2325
    new-instance v1, Lcom/classdojo/android/databinding/DialogTeacherInvitationBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogTeacherInvitationBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2327
    :cond_31e5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_teacher_invitation is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2133
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_31fc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2134
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_320b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2135
    :cond_320b
    const-string v1, "layout/dialog_student_list_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3219

    .line 2136
    new-instance v1, Lcom/classdojo/android/databinding/DialogStudentListBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogStudentListBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2138
    :cond_3219
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_student_list is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 84
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3230
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 85
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_323f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 86
    :cond_323f
    const-string v1, "layout/dialog_student_capture_student_list_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_324d

    .line 87
    new-instance v1, Lcom/classdojo/android/databinding/DialogStudentCaptureStudentListItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogStudentCaptureStudentListItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 89
    :cond_324d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_student_capture_student_list_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 225
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3264
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 226
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3273

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 227
    :cond_3273
    const-string v1, "layout/dialog_student_age_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3281

    .line 228
    new-instance v1, Lcom/classdojo/android/databinding/DialogStudentAgeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogStudentAgeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 230
    :cond_3281
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_student_age is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1359
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3298
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1360
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_32a7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1361
    :cond_32a7
    const-string v1, "layout/dialog_schedule_message_time_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_32b5

    .line 1362
    new-instance v1, Lcom/classdojo/android/databinding/DialogScheduleMessageTimeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogScheduleMessageTimeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1364
    :cond_32b5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_schedule_message_time is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 630
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_32cc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 631
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_32db

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 632
    :cond_32db
    const-string v1, "layout/dialog_password_changed_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_32e9

    .line 633
    new-instance v1, Lcom/classdojo/android/databinding/DialogPasswordChangedBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogPasswordChangedBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 635
    :cond_32e9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_password_changed is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1296
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3300
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1297
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_330f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1298
    :cond_330f
    const-string v1, "layout/dialog_parent_teacher_invitation_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_331d

    .line 1299
    new-instance v1, Lcom/classdojo/android/databinding/DialogParentTeacherInvitationBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogParentTeacherInvitationBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1301
    :cond_331d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_parent_teacher_invitation is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2187
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3334
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2188
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3343

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2189
    :cond_3343
    const-string v1, "layout/dialog_parent_invitation_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3351

    .line 2190
    new-instance v1, Lcom/classdojo/android/databinding/DialogParentInvitationBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogParentInvitationBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2192
    :cond_3351
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_parent_invitation is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1881
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3368
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1882
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3377

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1883
    :cond_3377
    const-string v1, "layout/dialog_parent_delete_connection_request_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3385

    .line 1884
    new-instance v1, Lcom/classdojo/android/databinding/DialogParentDeleteConnectionRequestBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogParentDeleteConnectionRequestBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1886
    :cond_3385
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_parent_delete_connection_request is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2097
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_339c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2098
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_33ab

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2099
    :cond_33ab
    const-string v1, "layout/dialog_invite_student_code_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_33b9

    .line 2100
    new-instance v1, Lcom/classdojo/android/databinding/DialogInviteStudentCodeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogInviteStudentCodeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2102
    :cond_33b9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_invite_student_code is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2304
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_33d0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2305
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_33df

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2306
    :cond_33df
    const-string v1, "layout/dialog_exit_student_mode_password_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_33ed

    .line 2307
    new-instance v1, Lcom/classdojo/android/databinding/DialogExitStudentModePasswordBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogExitStudentModePasswordBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2309
    :cond_33ed
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_exit_student_mode_password is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 549
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3404
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 550
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3413

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 551
    :cond_3413
    const-string v1, "layout/dialog_enter_student_mode_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3421

    .line 552
    new-instance v1, Lcom/classdojo/android/databinding/DialogEnterStudentModeBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogEnterStudentModeBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 554
    :cond_3421
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_enter_student_mode is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2022
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3438
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2023
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3447

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2024
    :cond_3447
    const-string v1, "layout/dialog_disconnect_student_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3455

    .line 2025
    new-instance v1, Lcom/classdojo/android/databinding/DialogDisconnectStudentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogDisconnectStudentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2027
    :cond_3455
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_disconnect_student is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1071
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_346c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1072
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_347b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1073
    :cond_347b
    const-string v1, "layout/dialog_class_code_student_select_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3489

    .line 1074
    new-instance v1, Lcom/classdojo/android/databinding/DialogClassCodeStudentSelectBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogClassCodeStudentSelectBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1076
    :cond_3489
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_class_code_student_select is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1917
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_34a0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1918
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_34af

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1919
    :cond_34af
    const-string v1, "layout/dialog_class_code_student_blocker_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_34bd

    .line 1920
    new-instance v1, Lcom/classdojo/android/databinding/DialogClassCodeStudentBlockerBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogClassCodeStudentBlockerBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1922
    :cond_34bd
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_class_code_student_blocker is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1314
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_34d4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1315
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_34e3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1316
    :cond_34e3
    const-string v1, "layout/dialog_change_name_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_34f1

    .line 1317
    new-instance v1, Lcom/classdojo/android/databinding/DialogChangeNameBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogChangeNameBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1319
    :cond_34f1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_change_name is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 639
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3508
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 640
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3517

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 641
    :cond_3517
    const-string v1, "layout/dialog_base_layout_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3525

    .line 642
    new-instance v1, Lcom/classdojo/android/databinding/DialogBaseLayoutBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogBaseLayoutBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 644
    :cond_3525
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_base_layout is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 12
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_353c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 13
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_354b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 14
    :cond_354b
    const-string v1, "layout/dialog_avatar_grid_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3559

    .line 15
    new-instance v1, Lcom/classdojo/android/databinding/DialogAvatarGridBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogAvatarGridBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 17
    :cond_3559
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_avatar_grid is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1233
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3570
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1234
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_357f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1235
    :cond_357f
    const-string v1, "layout/dialog_add_student_find_teacher_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_358d

    .line 1236
    new-instance v1, Lcom/classdojo/android/databinding/DialogAddStudentFindTeacherBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogAddStudentFindTeacherBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1238
    :cond_358d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_add_student_find_teacher is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1788
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_35a4
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1789
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_35b3

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1790
    :cond_35b3
    const-string v1, "layout/dialog_add_note_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_35c1

    .line 1791
    new-instance v1, Lcom/classdojo/android/databinding/DialogAddNoteBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/DialogAddNoteBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1793
    :cond_35c1
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for dialog_add_note is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2412
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_35d8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2413
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_35e7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2414
    :cond_35e7
    const-string v1, "layout/connection_type_header_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_35f5

    .line 2415
    new-instance v1, Lcom/classdojo/android/databinding/ConnectionTypeHeaderItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ConnectionTypeHeaderItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2417
    :cond_35f5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for connection_type_header_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 705
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_360c
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 706
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_361b

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 707
    :cond_361b
    const-string v1, "layout/combined_compose_audience_student_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3629

    .line 708
    new-instance v1, Lcom/classdojo/android/databinding/CombinedComposeAudienceStudentItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/CombinedComposeAudienceStudentItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 710
    :cond_3629
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for combined_compose_audience_student_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1206
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3640
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1207
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_364f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1208
    :cond_364f
    const-string v1, "layout/combined_compose_audience_group_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_365d

    .line 1209
    new-instance v1, Lcom/classdojo/android/databinding/CombinedComposeAudienceGroupItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/CombinedComposeAudienceGroupItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1211
    :cond_365d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for combined_compose_audience_group_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1062
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3674
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1063
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3683

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1064
    :cond_3683
    const-string v1, "layout/class_students_item_header_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3691

    .line 1065
    new-instance v1, Lcom/classdojo/android/databinding/ClassStudentsItemHeaderBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ClassStudentsItemHeaderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1067
    :cond_3691
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for class_students_item_header is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 987
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_36a8
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 988
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_36b7

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 989
    :cond_36b7
    const-string v1, "layout/class_students_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_36c5

    .line 990
    new-instance v1, Lcom/classdojo/android/databinding/ClassStudentsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ClassStudentsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 992
    :cond_36c5
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for class_students_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 396
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_36dc
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 397
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_36eb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 398
    :cond_36eb
    const-string v1, "layout/binding_variable_placeholder_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_36f9

    .line 399
    new-instance v1, Lcz/kinst/jakub/viewmodelbinding/databinding/BindingVariablePlaceholderBinding;

    invoke-direct {v1, p1, p2}, Lcz/kinst/jakub/viewmodelbinding/databinding/BindingVariablePlaceholderBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 401
    :cond_36f9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for binding_variable_placeholder is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 621
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3710
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 622
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_371f

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 623
    :cond_371f
    const-string v1, "layout/add_note_undo_popup_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_372d

    .line 624
    new-instance v1, Lcom/classdojo/android/databinding/AddNoteUndoPopupBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/AddNoteUndoPopupBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 626
    :cond_372d
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for add_note_undo_popup is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 657
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3744
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 658
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3753

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 659
    :cond_3753
    const-string v1, "layout/activity_teacher_onboarding_check_email_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3761

    .line 660
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherOnboardingCheckEmailBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherOnboardingCheckEmailBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 662
    :cond_3761
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_teacher_onboarding_check_email is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1872
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3778
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1873
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3787

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1874
    :cond_3787
    const-string v1, "layout/activity_teacher_home_null_state_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3795

    .line 1875
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherHomeNullStateBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherHomeNullStateBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1877
    :cond_3795
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_teacher_home_null_state is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 777
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_37ac
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 778
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_37bb

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 779
    :cond_37bb
    const-string v1, "layout/activity_teacher_home_drawer_header_light_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_37c9

    .line 780
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherHomeDrawerHeaderLightBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherHomeDrawerHeaderLightBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 782
    :cond_37c9
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_teacher_home_drawer_header_light is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 369
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_37e0
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 370
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_37ef

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 371
    :cond_37ef
    const-string v1, "layout/activity_teacher_home_drawer_header_dark_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_37fd

    .line 372
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherHomeDrawerHeaderDarkBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherHomeDrawerHeaderDarkBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 374
    :cond_37fd
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_teacher_home_drawer_header_dark is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 813
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3814
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 814
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3823

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 815
    :cond_3823
    const-string v1, "layout-sw600dp-land/activity_teacher_home_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3831

    .line 816
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherHomeBindingSw600dpLandImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherHomeBindingSw600dpLandImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 818
    :cond_3831
    const-string v1, "layout/activity_teacher_home_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_383f

    .line 819
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherHomeBindingImpl;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherHomeBindingImpl;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 821
    :cond_383f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_teacher_home is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1125
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3856
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1126
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3865

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1127
    :cond_3865
    const-string v1, "layout/activity_teacher_behavior_controller_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3873

    .line 1128
    new-instance v1, Lcom/classdojo/android/databinding/ActivityTeacherBehaviorControllerBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityTeacherBehaviorControllerBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1130
    :cond_3873
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_teacher_behavior_controller is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 741
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_388a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 742
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3899

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 743
    :cond_3899
    const-string v1, "layout/activity_student_settings_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_38a7

    .line 744
    new-instance v1, Lcom/classdojo/android/databinding/ActivityStudentSettingsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityStudentSettingsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 746
    :cond_38a7
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_student_settings is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1527
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_38be
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1528
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_38cd

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1529
    :cond_38cd
    const-string v1, "layout/activity_student_search_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_38db

    .line 1530
    new-instance v1, Lcom/classdojo/android/databinding/ActivityStudentSearchBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityStudentSearchBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1532
    :cond_38db
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_student_search is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1305
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_38f2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1306
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3901

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1307
    :cond_3901
    const-string v1, "layout/activity_share_media_content_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_390f

    .line 1308
    new-instance v1, Lcom/classdojo/android/databinding/ActivityShareMediaContentBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityShareMediaContentBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1310
    :cond_390f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_share_media_content is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 768
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3926
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 769
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3935

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 770
    :cond_3935
    const-string v1, "layout/activity_share_media_audience_selector_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3943

    .line 771
    new-instance v1, Lcom/classdojo/android/databinding/ActivityShareMediaAudienceSelectorBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityShareMediaAudienceSelectorBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 773
    :cond_3943
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_share_media_audience_selector is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 279
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_395a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 280
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3969

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 281
    :cond_3969
    const-string v1, "layout/activity_share_media_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3977

    .line 282
    new-instance v1, Lcom/classdojo/android/databinding/ActivityShareMediaBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityShareMediaBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 284
    :cond_3977
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_share_media is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2151
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_398e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2152
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_399d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2153
    :cond_399d
    const-string v1, "layout/activity_setup_skills_sharing_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_39ab

    .line 2154
    new-instance v1, Lcom/classdojo/android/databinding/ActivitySetupSkillsSharingBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivitySetupSkillsSharingBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2156
    :cond_39ab
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_setup_skills_sharing is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 48
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_39c2
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 49
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_39d1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 50
    :cond_39d1
    const-string v1, "layout/activity_setup_skills_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_39df

    .line 51
    new-instance v1, Lcom/classdojo/android/databinding/ActivitySetupSkillsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivitySetupSkillsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 53
    :cond_39df
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_setup_skills is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 906
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_39f6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 907
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3a05

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 908
    :cond_3a05
    const-string v1, "layout/activity_onboarding_teacher_sign_up_flow_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3a13

    .line 909
    new-instance v1, Lcom/classdojo/android/databinding/ActivityOnboardingTeacherSignUpFlowBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityOnboardingTeacherSignUpFlowBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 911
    :cond_3a13
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_onboarding_teacher_sign_up_flow is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2466
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3a2a
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2467
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3a39

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2468
    :cond_3a39
    const-string v1, "layout/activity_onboarding_parent_sign_up_flow_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3a47

    .line 2469
    new-instance v1, Lcom/classdojo/android/databinding/ActivityOnboardingParentSignUpFlowBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityOnboardingParentSignUpFlowBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2471
    :cond_3a47
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_onboarding_parent_sign_up_flow is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 915
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3a5e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 916
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3a6d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 917
    :cond_3a6d
    const-string v1, "layout/activity_dojo_camera_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3a7b

    .line 918
    new-instance v1, Lcom/classdojo/android/databinding/ActivityDojoCameraBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityDojoCameraBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 920
    :cond_3a7b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_dojo_camera is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1242
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3a92
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1243
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3aa1

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1244
    :cond_3aa1
    const-string v1, "layout/activity_debug_urls_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3aaf

    .line 1245
    new-instance v1, Lcom/classdojo/android/databinding/ActivityDebugUrlsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityDebugUrlsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1247
    :cond_3aaf
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_debug_urls is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 951
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3ac6
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 952
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3ad5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 953
    :cond_3ad5
    const-string v1, "layout/activity_debug_deeplinks_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3ae3

    .line 954
    new-instance v1, Lcom/classdojo/android/databinding/ActivityDebugDeeplinksBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityDebugDeeplinksBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 956
    :cond_3ae3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_debug_deeplinks is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2223
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3afa
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 2224
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3b09

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 2225
    :cond_3b09
    const-string v1, "layout/activity_debug_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3b17

    .line 2226
    new-instance v1, Lcom/classdojo/android/databinding/ActivityDebugBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityDebugBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 2228
    :cond_3b17
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_debug is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 306
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3b2e
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 307
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3b3d

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 308
    :cond_3b3d
    const-string v1, "layout/activity_connection_requests_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3b4b

    .line 309
    new-instance v1, Lcom/classdojo/android/databinding/ActivityConnectionRequestsBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityConnectionRequestsBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 311
    :cond_3b4b
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_connection_requests is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 486
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3b62
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 487
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3b71

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 488
    :cond_3b71
    const-string v1, "layout/activity_connection_reqeusts_item_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3b7f

    .line 489
    new-instance v1, Lcom/classdojo/android/databinding/ActivityConnectionReqeustsItemBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityConnectionReqeustsItemBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 491
    :cond_3b7f
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_connection_reqeusts_item is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1974
    .end local v0    # "tag":Ljava/lang/Object;
    :pswitch_3b96
    invoke-virtual {p2}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v0

    .line 1975
    .restart local v0    # "tag":Ljava/lang/Object;
    if-nez v0, :cond_3ba5

    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "view must have a tag"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    .line 1976
    :cond_3ba5
    const-string v1, "layout/activity_combined_camera_text_post_0"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_3bb3

    .line 1977
    new-instance v1, Lcom/classdojo/android/databinding/ActivityCombinedCameraTextPostBinding;

    invoke-direct {v1, p1, p2}, Lcom/classdojo/android/databinding/ActivityCombinedCameraTextPostBinding;-><init>(Landroid/databinding/DataBindingComponent;Landroid/view/View;)V

    return-object v1

    .line 1979
    :cond_3bb3
    new-instance v1, Ljava/lang/IllegalArgumentException;

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "The tag for activity_combined_camera_text_post is invalid. Received: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-direct {v1, v2}, Ljava/lang/IllegalArgumentException;-><init>(Ljava/lang/String;)V

    throw v1

    nop

    :pswitch_data_3bcc
    .packed-switch 0x7f0c002a
        :pswitch_3b96
        :pswitch_3b62
        :pswitch_3b2e
    .end packed-switch

    :pswitch_data_3bd6
    .packed-switch 0x7f0c0031
        :pswitch_3afa
        :pswitch_3ac6
        :pswitch_3a92
        :pswitch_3a5e
    .end packed-switch

    :pswitch_data_3be2
    .packed-switch 0x7f0c0041
        :pswitch_3a2a
        :pswitch_39f6
    .end packed-switch

    :pswitch_data_3bea
    .packed-switch 0x7f0c0057
        :pswitch_39c2
        :pswitch_398e
        :pswitch_395a
        :pswitch_3926
        :pswitch_38f2
    .end packed-switch

    :pswitch_data_3bf8
    .packed-switch 0x7f0c0067
        :pswitch_38be
        :pswitch_388a
    .end packed-switch

    :pswitch_data_3c00
    .packed-switch 0x7f0c006a
        :pswitch_3856
        :pswitch_3814
        :pswitch_37e0
        :pswitch_37ac
        :pswitch_3778
        :pswitch_3744
    .end packed-switch

    :pswitch_data_3c10
    .packed-switch 0x7f0c0076
        :pswitch_3710
        :pswitch_36dc
    .end packed-switch

    :pswitch_data_3c18
    .packed-switch 0x7f0c0084
        :pswitch_36a8
        :pswitch_3674
        :pswitch_3640
    .end packed-switch

    :pswitch_data_3c22
    .packed-switch 0x7f0c0088
        :pswitch_360c
        :pswitch_35d8
    .end packed-switch

    :pswitch_data_3c2a
    .packed-switch 0x7f0c00a3
        :pswitch_35a4
        :pswitch_3570
    .end packed-switch

    :pswitch_data_3c32
    .packed-switch 0x7f0c00a6
        :pswitch_353c
        :pswitch_3508
        :pswitch_34d4
        :pswitch_34a0
        :pswitch_346c
    .end packed-switch

    :pswitch_data_3c40
    .packed-switch 0x7f0c00ac
        :pswitch_3438
        :pswitch_3404
        :pswitch_33d0
        :pswitch_339c
    .end packed-switch

    :pswitch_data_3c4c
    .packed-switch 0x7f0c00b1
        :pswitch_3368
        :pswitch_3334
        :pswitch_3300
        :pswitch_32cc
    .end packed-switch

    :pswitch_data_3c58
    .packed-switch 0x7f0c00b8
        :pswitch_3298
        :pswitch_3264
        :pswitch_3230
    .end packed-switch

    :pswitch_data_3c62
    .packed-switch 0x7f0c00bc
        :pswitch_31fc
        :pswitch_31c8
        :pswitch_3194
        :pswitch_3160
        :pswitch_312c
        :pswitch_30f8
    .end packed-switch

    :pswitch_data_3c72
    .packed-switch 0x7f0c00cb
        :pswitch_30c4
        :pswitch_3090
        :pswitch_305c
        :pswitch_301a
        :pswitch_2fe6
        :pswitch_2fb2
        :pswitch_2f7e
        :pswitch_2f4a
        :pswitch_2f16
        :pswitch_2ee2
    .end packed-switch

    :pswitch_data_3c8a
    .packed-switch 0x7f0c00d6
        :pswitch_2eae
        :pswitch_2e7a
        :pswitch_2e46
        :pswitch_2e12
        :pswitch_2dde
        :pswitch_2daa
        :pswitch_2d76
        :pswitch_2d42
        :pswitch_2d0e
        :pswitch_2cda
        :pswitch_2ca6
        :pswitch_2c72
        :pswitch_2c3e
        :pswitch_2c0a
        :pswitch_2bd6
        :pswitch_2ba2
        :pswitch_2b6e
        :pswitch_2b3a
        :pswitch_2b06
        :pswitch_2ad2
        :pswitch_2a9e
        :pswitch_2a6a
        :pswitch_2a36
        :pswitch_2a02
        :pswitch_29ce
        :pswitch_299a
        :pswitch_2966
        :pswitch_2924
        :pswitch_28f0
        :pswitch_28bc
        :pswitch_2888
        :pswitch_2854
        :pswitch_2820
        :pswitch_27ec
    .end packed-switch

    :pswitch_data_3cd2
    .packed-switch 0x7f0c00f9
        :pswitch_27b8
        :pswitch_2784
        :pswitch_2750
        :pswitch_271c
    .end packed-switch

    :pswitch_data_3cde
    .packed-switch 0x7f0c0100
        :pswitch_26e8
        :pswitch_26b4
        :pswitch_2680
        :pswitch_264c
        :pswitch_2618
        :pswitch_25d6
        :pswitch_25a2
        :pswitch_256e
        :pswitch_253a
        :pswitch_2506
        :pswitch_24d2
        :pswitch_249e
        :pswitch_246a
        :pswitch_2436
        :pswitch_2402
        :pswitch_23ce
        :pswitch_239a
        :pswitch_2366
        :pswitch_2332
        :pswitch_22fe
        :pswitch_22ca
        :pswitch_2296
        :pswitch_2262
        :pswitch_222e
        :pswitch_21fa
        :pswitch_21c6
        :pswitch_2192
        :pswitch_215e
        :pswitch_212a
        :pswitch_20f6
        :pswitch_20c2
        :pswitch_208e
        :pswitch_205a
        :pswitch_2026
        :pswitch_1ff2
        :pswitch_1fbe
    .end packed-switch

    :pswitch_data_3d2a
    .packed-switch 0x7f0c0127
        :pswitch_1f8a
        :pswitch_1f56
        :pswitch_1f22
        :pswitch_1eee
        :pswitch_1eba
        :pswitch_1e86
        :pswitch_1e52
        :pswitch_1e1e
        :pswitch_1dea
        :pswitch_1db6
        :pswitch_1d82
        :pswitch_1d4e
        :pswitch_1d1a
        :pswitch_1ce6
        :pswitch_1cb2
        :pswitch_1c7e
        :pswitch_1c4a
        :pswitch_1c16
        :pswitch_1be2
        :pswitch_1bae
        :pswitch_1b7a
        :pswitch_1b46
        :pswitch_1b12
        :pswitch_1ade
        :pswitch_1aaa
        :pswitch_1a76
        :pswitch_1a42
        :pswitch_1a0e
        :pswitch_19be
        :pswitch_197c
        :pswitch_1948
        :pswitch_1914
        :pswitch_18e0
        :pswitch_18ac
        :pswitch_1878
        :pswitch_1844
        :pswitch_1810
        :pswitch_17dc
        :pswitch_17a8
        :pswitch_1766
        :pswitch_1732
        :pswitch_16fe
        :pswitch_16ca
        :pswitch_1696
        :pswitch_1662
        :pswitch_162e
        :pswitch_15fa
        :pswitch_15c6
        :pswitch_1592
        :pswitch_155e
    .end packed-switch

    :pswitch_data_3d92
    .packed-switch 0x7f0c015f
        :pswitch_152a
        :pswitch_14f6
    .end packed-switch

    :pswitch_data_3d9a
    .packed-switch 0x7f0c0162
        :pswitch_14c2
        :pswitch_148e
        :pswitch_145a
        :pswitch_1426
        :pswitch_13f2
    .end packed-switch

    :pswitch_data_3da8
    .packed-switch 0x7f0c016e
        :pswitch_13be
        :pswitch_138a
        :pswitch_1356
    .end packed-switch

    :pswitch_data_3db2
    .packed-switch 0x7f0c0174
        :pswitch_1322
        :pswitch_12ee
        :pswitch_12ba
        :pswitch_1286
        :pswitch_1252
        :pswitch_121e
        :pswitch_11dc
        :pswitch_11a8
        :pswitch_1174
        :pswitch_1140
        :pswitch_110c
        :pswitch_10d8
        :pswitch_10a4
        :pswitch_1070
    .end packed-switch

    :pswitch_data_3dd2
    .packed-switch 0x7f0c0183
        :pswitch_103c
        :pswitch_1008
        :pswitch_fd4
        :pswitch_fa0
        :pswitch_f6c
        :pswitch_f38
        :pswitch_f04
        :pswitch_ed0
    .end packed-switch

    :pswitch_data_3de6
    .packed-switch 0x7f0c018f
        :pswitch_e9c
        :pswitch_e68
        :pswitch_e34
        :pswitch_e00
    .end packed-switch

    :pswitch_data_3df2
    .packed-switch 0x7f0c019b
        :pswitch_dcc
        :pswitch_d98
        :pswitch_d64
    .end packed-switch

    :pswitch_data_3dfc
    .packed-switch 0x7f0c01a6
        :pswitch_d30
        :pswitch_cfc
        :pswitch_cc8
        :pswitch_c94
    .end packed-switch

    :pswitch_data_3e08
    .packed-switch 0x7f0c01ab
        :pswitch_c60
        :pswitch_c2c
        :pswitch_bf8
    .end packed-switch

    :pswitch_data_3e12
    .packed-switch 0x7f0c01af
        :pswitch_bc4
        :pswitch_b90
        :pswitch_b5c
        :pswitch_b28
        :pswitch_af4
    .end packed-switch

    :pswitch_data_3e20
    .packed-switch 0x7f0c01de
        :pswitch_ac0
        :pswitch_a8c
        :pswitch_a58
    .end packed-switch

    :pswitch_data_3e2a
    .packed-switch 0x7f0c01f8
        :pswitch_a24
        :pswitch_9e2
        :pswitch_9ae
    .end packed-switch

    :pswitch_data_3e34
    .packed-switch 0x7f0c0208
        :pswitch_97a
        :pswitch_946
        :pswitch_912
        :pswitch_8de
        :pswitch_8aa
    .end packed-switch

    :pswitch_data_3e42
    .packed-switch 0x7f0c0210
        :pswitch_868
        :pswitch_834
        :pswitch_800
        :pswitch_7cc
        :pswitch_798
        :pswitch_764
    .end packed-switch

    :pswitch_data_3e52
    .packed-switch 0x7f0c0218
        :pswitch_730
        :pswitch_6fc
        :pswitch_6c8
    .end packed-switch

    :sswitch_data_3e5c
    .sparse-switch
        0x7f0c001d -> :sswitch_694
        0x7f0c0026 -> :sswitch_660
        0x7f0c0037 -> :sswitch_62c
        0x7f0c0046 -> :sswitch_5f8
        0x7f0c0048 -> :sswitch_5c4
        0x7f0c004b -> :sswitch_590
        0x7f0c004d -> :sswitch_55c
        0x7f0c0052 -> :sswitch_528
        0x7f0c0054 -> :sswitch_4f4
        0x7f0c005f -> :sswitch_4b2
        0x7f0c007f -> :sswitch_47e
        0x7f0c0090 -> :sswitch_44a
        0x7f0c00c9 -> :sswitch_416
        0x7f0c00fe -> :sswitch_3e2
        0x7f0c0125 -> :sswitch_3ae
        0x7f0c015a -> :sswitch_37a
        0x7f0c016b -> :sswitch_346
        0x7f0c018c -> :sswitch_312
        0x7f0c0195 -> :sswitch_2de
        0x7f0c019f -> :sswitch_2aa
        0x7f0c01a2 -> :sswitch_276
        0x7f0c01a4 -> :sswitch_242
        0x7f0c01e5 -> :sswitch_20e
        0x7f0c01e7 -> :sswitch_1da
        0x7f0c01e9 -> :sswitch_1a6
        0x7f0c01f3 -> :sswitch_172
        0x7f0c01f6 -> :sswitch_13e
        0x7f0c01fe -> :sswitch_10a
        0x7f0c0200 -> :sswitch_d6
        0x7f0c0202 -> :sswitch_a2
        0x7f0c0205 -> :sswitch_6e
    .end sparse-switch
.end method

.method public getDataBinder(Landroid/databinding/DataBindingComponent;[Landroid/view/View;I)Landroid/databinding/ViewDataBinding;
    .registers 5
    .param p1, "bindingComponent"    # Landroid/databinding/DataBindingComponent;
    .param p2, "views"    # [Landroid/view/View;
    .param p3, "layoutId"    # I

    .line 2652
    nop

    .line 2654
    const/4 v0, 0x0

    return-object v0
.end method

.method public getLayoutId(Ljava/lang/String;)I
    .registers 15
    .param p1, "tag"    # Ljava/lang/String;

    .line 2658
    const/4 v0, 0x0

    if-nez p1, :cond_4

    .line 2659
    return v0

    .line 2661
    :cond_4
    invoke-virtual {p1}, Ljava/lang/String;->hashCode()I

    move-result v1

    .line 2662
    .local v1, "code":I
    const v2, 0x7f0c0144

    const v3, 0x7f0c0105

    const v4, 0x7f0c00ce

    const v5, 0x7f0c014e

    const v6, 0x7f0c006b

    const v7, 0x7f0c0210

    const v8, 0x7f0c01f9

    const v9, 0x7f0c017a

    const v10, 0x7f0c005f

    const v11, 0x7f0c00f1

    const v12, 0x7f0c0143

    sparse-switch v1, :sswitch_data_e06

    goto/16 :goto_e05

    .line 3798
    :sswitch_2e
    const-string v2, "layout/fragment_student_capture_home_student_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3799
    const v0, 0x7f0c0140

    return v0

    .line 2670
    :sswitch_3a
    const-string v2, "layout/fragment_class_wall_settings_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2671
    const v0, 0x7f0c00e6

    return v0

    .line 4410
    :sswitch_46
    const-string v2, "layout/fragment_enter_student_mode_qr_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4411
    const v0, 0x7f0c00fb

    return v0

    .line 4104
    :sswitch_52
    const-string v2, "layout/item_file_attachment_view_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4105
    const v0, 0x7f0c0191

    return v0

    .line 3768
    :sswitch_5e
    const-string v2, "layout/fragment_kids_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3769
    const v0, 0x7f0c0102

    return v0

    .line 3432
    :sswitch_6a
    const-string v2, "layout/fragment_school_detail_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3433
    const v0, 0x7f0c0127

    return v0

    .line 2718
    :sswitch_76
    const-string v2, "layout/fragment_tab_notification_pending_posts_item_thumbnail_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2719
    const v0, 0x7f0c0179

    return v0

    .line 2982
    :sswitch_82
    const-string v2, "layout/fragment_onboarding_enter_code_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2983
    const v0, 0x7f0c0109

    return v0

    .line 3750
    :sswitch_8e
    const-string v2, "layout/view_drawing_tool_sticker_drawer_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3751
    const v0, 0x7f0c0212

    return v0

    .line 3024
    :sswitch_9a
    const-string v2, "layout/fragment_tab_class_wall_item_compose_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3025
    const v0, 0x7f0c015f

    return v0

    .line 3006
    :sswitch_a6
    const-string v2, "layout/dialog_teacher_student_consent_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3007
    const v0, 0x7f0c00bf

    return v0

    .line 4374
    :sswitch_b2
    const-string v2, "layout/fragment_teacher_student_connection_class_code_qr_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4375
    const v0, 0x7f0c0184

    return v0

    .line 4008
    :sswitch_be
    const-string v2, "layout/activity_combined_camera_text_post_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4009
    const v0, 0x7f0c002a

    return v0

    .line 3018
    :sswitch_ca
    const-string v2, "layout/fragment_parent_checklist_birthday_capture_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3019
    const v0, 0x7f0c0113

    return v0

    .line 4332
    :sswitch_d6
    const-string v2, "layout/fragment_school_directory_item_student_parent_circle_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4333
    const v0, 0x7f0c012d

    return v0

    .line 3384
    :sswitch_e2
    const-string v2, "layout/class_students_item_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3385
    const v0, 0x7f0c0085

    return v0

    .line 2922
    :sswitch_ee
    const-string v2, "layout/dialog_turn_on_password_lock_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2923
    const v0, 0x7f0c00c0

    return v0

    .line 4026
    :sswitch_fa
    const-string v2, "layout-sw600dp/fragment_dojo_camera_controls_0_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4027
    return v11

    .line 3762
    :sswitch_103
    const-string v2, "layout/webview_fragment_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3763
    const v0, 0x7f0c021a

    return v0

    .line 3222
    :sswitch_10f
    const-string v2, "layout/toolbar_gray_layout_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3223
    const v0, 0x7f0c0202

    return v0

    .line 3402
    :sswitch_11b
    const-string v2, "layout/fragment_dojo_photo_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3403
    const v0, 0x7f0c00f6

    return v0

    .line 4320
    :sswitch_127
    const-string v2, "layout/fragment_attendance_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4321
    const v0, 0x7f0c00d7

    return v0

    .line 4050
    :sswitch_133
    const-string v2, "layout/dialog_unsupported_class_code_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4051
    const v0, 0x7f0c00c1

    return v0

    .line 3330
    :sswitch_13f
    const-string v2, "layout/class_students_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3331
    const v0, 0x7f0c0084

    return v0

    .line 4338
    :sswitch_14b
    const-string v2, "layout/fragment_onboarding_student_avatar_editor_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4339
    const v0, 0x7f0c010f

    return v0

    .line 4128
    :sswitch_157
    const-string v2, "layout/fragment_scheduled_messages_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4129
    const v0, 0x7f0c0123

    return v0

    .line 3258
    :sswitch_163
    const-string v2, "layout/fragment_profile_photo_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3259
    const v0, 0x7f0c011f

    return v0

    .line 3534
    :sswitch_16f
    const-string v2, "layout/fragment_tab_class_wall_item_student_avatar_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3535
    const v0, 0x7f0c016b

    return v0

    .line 4464
    :sswitch_17b
    const-string v2, "layout/layout_parent_connections_initial_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4465
    const v0, 0x7f0c01ab

    return v0

    .line 3936
    :sswitch_187
    const-string v2, "layout/activity_teacher_home_null_state_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3937
    const v0, 0x7f0c006e

    return v0

    .line 3396
    :sswitch_193
    const-string v2, "layout/fragment_chat_empty_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3397
    const v0, 0x7f0c00de

    return v0

    .line 4002
    :sswitch_19f
    const-string v2, "layout/item_teacher_student_text_code_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4003
    const v0, 0x7f0c01a6

    return v0

    .line 4272
    :sswitch_1ab
    const-string v2, "layout/layout_progress_footer_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4273
    const v0, 0x7f0c01af

    return v0

    .line 4200
    :sswitch_1b7
    const-string v2, "layout/view_parent_pending_connection_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4201
    const v0, 0x7f0c0215

    return v0

    .line 4140
    :sswitch_1c3
    const-string v2, "layout/fragment_create_school_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4141
    const v0, 0x7f0c00ef

    return v0

    .line 3522
    :sswitch_1cf
    const-string v2, "layout/fragment_parent_connection_request_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3523
    const v0, 0x7f0c0115

    return v0

    .line 3576
    :sswitch_1db
    const-string v2, "layout/fragment_edit_behaviours_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3577
    const v0, 0x7f0c00f7

    return v0

    .line 3390
    :sswitch_1e7
    const-string v2, "layout/dialog_class_code_student_select_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3391
    const v0, 0x7f0c00aa

    return v0

    .line 3516
    :sswitch_1f3
    const-string v2, "layout/fragment_teacher_channel_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3517
    const v0, 0x7f0c017e

    return v0

    .line 3264
    :sswitch_1ff
    const-string v2, "layout/fragment_tab_class_wall_item_parent_empty_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3265
    const v0, 0x7f0c0165

    return v0

    .line 3894
    :sswitch_20b
    const-string v2, "layout/fragment_group_students_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3895
    const v0, 0x7f0c00fe

    return v0

    .line 2886
    :sswitch_217
    const-string v2, "layout/activity_passwordless_login_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2887
    const v0, 0x7f0c004d

    return v0

    .line 2832
    :sswitch_223
    const-string v2, "layout/fragment_story_share_to_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2833
    const v0, 0x7f0c013c

    return v0

    .line 3990
    :sswitch_22f
    const-string v2, "layout/fragment_class_list_item_add_class_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3991
    const v0, 0x7f0c00e1

    return v0

    .line 3606
    :sswitch_23b
    const-string v2, "layout/layout_student_connections_invite_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3607
    const v0, 0x7f0c01b1

    return v0

    .line 4098
    :sswitch_247
    const-string v2, "layout/dialog_invite_student_code_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4099
    const v0, 0x7f0c00af

    return v0

    .line 4032
    :sswitch_253
    const-string v2, "layout/fragment_dojo_camera_controls_0_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4033
    return v11

    .line 3288
    :sswitch_25c
    const-string v2, "layout/toolbar_teacher_home_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3289
    const v0, 0x7f0c0209

    return v0

    .line 3834
    :sswitch_268
    const-string v2, "layout/fragment_edit_students_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3835
    const v0, 0x7f0c00f9

    return v0

    .line 2856
    :sswitch_274
    const-string v2, "layout/fragment_student_report_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2857
    const v0, 0x7f0c0152

    return v0

    .line 3582
    :sswitch_280
    const-string v2, "layout/dialog_schedule_message_time_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3583
    const v0, 0x7f0c00b8

    return v0

    .line 4290
    :sswitch_28c
    const-string v2, "layout/fragment_teacher_settings_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4291
    const v0, 0x7f0c0181

    return v0

    .line 4398
    :sswitch_298
    const-string v2, "layout/fragment_chat_item_sticker_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4399
    const v0, 0x7f0c00df

    return v0

    .line 2724
    :sswitch_2a4
    const-string v2, "layout/fragment_photo_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2725
    const v0, 0x7f0c011d

    return v0

    .line 4386
    :sswitch_2b0
    const-string v2, "layout/fragment_teacher_approval_feed_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4387
    const v0, 0x7f0c017c

    return v0

    .line 3102
    :sswitch_2bc
    const-string v2, "layout/activity_teacher_onboarding_check_email_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3103
    const v0, 0x7f0c006f

    return v0

    .line 2730
    :sswitch_2c8
    const-string v2, "layout/fragment_onboarding_sign_up_details_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2731
    const v0, 0x7f0c010a

    return v0

    .line 4176
    :sswitch_2d4
    const-string v2, "layout/layout_student_connections_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4177
    const v0, 0x7f0c01b2

    return v0

    .line 3666
    :sswitch_2e0
    const-string v2, "layout/item_teacher_add_coteacher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3667
    const v0, 0x7f0c01a4

    return v0

    .line 2946
    :sswitch_2ec
    const-string v2, "layout/fragment_student_capture_mark_students_marked_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2947
    const v0, 0x7f0c0142

    return v0

    .line 3240
    :sswitch_2f8
    const-string v2, "layout/fragment_parent_school_search_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3241
    const v0, 0x7f0c0117

    return v0

    .line 3540
    :sswitch_304
    const-string v2, "layout/dialog_parent_teacher_invitation_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3541
    const v0, 0x7f0c00b3

    return v0

    .line 4116
    :sswitch_310
    const-string v2, "layout/student_connections_individual_codes_invite_section_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4117
    const v0, 0x7f0c01f8

    return v0

    .line 3696
    :sswitch_31c
    const-string v2, "layout/popup_item_copy_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3697
    const v0, 0x7f0c01e9

    return v0

    .line 3858
    :sswitch_328
    const-string v2, "layout/fragment_combined_camera_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3859
    const v0, 0x7f0c00e8

    return v0

    .line 4134
    :sswitch_334
    const-string v2, "layout/activity_setup_skills_sharing_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4135
    const v0, 0x7f0c0058

    return v0

    .line 3630
    :sswitch_340
    const-string v2, "layout-sw600dp-land/activity_student_capture_home_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3631
    return v10

    .line 2754
    :sswitch_349
    const-string v2, "layout/fragment_class_list_item_class_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2755
    const v0, 0x7f0c00e2

    return v0

    .line 3312
    :sswitch_355
    const-string v2, "layout/activity_parent_setup_student_account_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3313
    const v0, 0x7f0c004b

    return v0

    .line 4182
    :sswitch_361
    const-string v2, "layout/activity_debug_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4183
    const v0, 0x7f0c0031

    return v0

    .line 3564
    :sswitch_36d
    const-string v2, "layout/fragment_student_capture_student_list_home_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3565
    const v0, 0x7f0c0147

    return v0

    .line 3054
    :sswitch_379
    const-string v2, "layout/placeholder_offline_with_refresh_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3055
    const v0, 0x7f0c01e7

    return v0

    .line 3684
    :sswitch_385
    const-string v2, "layout/fragment_audience_selector_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3685
    const v0, 0x7f0c00d8

    return v0

    .line 3732
    :sswitch_391
    const-string v2, "layout/activity_parent_home_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3733
    const v0, 0x7f0c0048

    return v0

    .line 4356
    :sswitch_39d
    const-string v2, "layout/fragment_student_codes_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4357
    const v0, 0x7f0c014b

    return v0

    .line 3654
    :sswitch_3a9
    const-string v2, "layout/fragment_tab_notification_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3655
    const v0, 0x7f0c0175

    return v0

    .line 3282
    :sswitch_3b5
    const-string v2, "layout/activity_dojo_camera_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3283
    const v0, 0x7f0c0034

    return v0

    .line 3978
    :sswitch_3c1
    const-string v2, "layout-sw600dp-land/fragment_tab_story_feed_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3979
    return v9

    .line 4044
    :sswitch_3ca
    const-string v2, "layout/dialog_disconnect_student_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4045
    const v0, 0x7f0c00ac

    return v0

    .line 3912
    :sswitch_3d6
    const-string v2, "layout-sw600dp/student_login_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3913
    return v8

    .line 2970
    :sswitch_3df
    const-string v2, "layout/fragment_student_capture_student_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2971
    const v0, 0x7f0c0148

    return v0

    .line 3036
    :sswitch_3eb
    const-string v2, "layout/fragment_school_detail_teacher_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3037
    const v0, 0x7f0c0128

    return v0

    .line 3468
    :sswitch_3f7
    const-string v2, "layout/fragment_student_capture_story_feed_item_content_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3469
    const v0, 0x7f0c0145

    return v0

    .line 3876
    :sswitch_403
    const-string v2, "layout/dialog_add_note_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3877
    const v0, 0x7f0c00a3

    return v0

    .line 3372
    :sswitch_40f
    const-string v2, "layout/item_image_resource_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3373
    const v0, 0x7f0c0195

    return v0

    .line 4230
    :sswitch_41b
    const-string v2, "layout/fragment_add_edit_student_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4231
    const v0, 0x7f0c00d3

    return v0

    .line 3060
    :sswitch_427
    const-string v2, "layout/fragment_student_report_add_note_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3061
    const v0, 0x7f0c0153

    return v0

    .line 2682
    :sswitch_433
    const-string v2, "layout/fragment_combined_fullscreen_photo_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2683
    const v0, 0x7f0c00ed

    return v0

    .line 3108
    :sswitch_43f
    const-string v2, "layout/fragment_class_wall_compose_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3109
    const v0, 0x7f0c00e5

    return v0

    .line 3966
    :sswitch_44b
    const-string v2, "layout/dialog_class_code_student_blocker_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3967
    const v0, 0x7f0c00a9

    return v0

    .line 2868
    :sswitch_457
    const-string v2, "layout/activity_connection_requests_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2869
    const v0, 0x7f0c002c

    return v0

    .line 2988
    :sswitch_463
    const-string v2, "layout/activity_connection_reqeusts_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2989
    const v0, 0x7f0c002b

    return v0

    .line 2796
    :sswitch_46f
    const-string v2, "layout/fragment_school_directory_item_welcome_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2797
    const v0, 0x7f0c0131

    return v0

    .line 3726
    :sswitch_47b
    const-string v2, "layout/fragment_story_share_to_class_header_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3727
    const v0, 0x7f0c013b

    return v0

    .line 3888
    :sswitch_487
    const-string v2, "layout/fragment_tab_class_wall_item_parent_empty_checklist_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3889
    const v0, 0x7f0c0166

    return v0

    .line 4254
    :sswitch_493
    const-string v2, "layout/fragment_school_directory_item_teacher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4255
    const v0, 0x7f0c012e

    return v0

    .line 2772
    :sswitch_49f
    const-string v2, "layout/fragment_parent_teacher_search_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2773
    const v0, 0x7f0c011c

    return v0

    .line 3450
    :sswitch_4ab
    const-string v2, "layout/item_parent_list_new_message_invite_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3451
    const v0, 0x7f0c019f

    return v0

    .line 3426
    :sswitch_4b7
    const-string v2, "layout/activity_teacher_behavior_controller_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3427
    const v0, 0x7f0c006a

    return v0

    .line 3090
    :sswitch_4c3
    const-string v2, "layout/dialog_base_layout_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3091
    const v0, 0x7f0c00a7

    return v0

    .line 3276
    :sswitch_4cf
    const-string v2, "layout/activity_onboarding_teacher_sign_up_flow_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3277
    const v0, 0x7f0c0042

    return v0

    .line 2712
    :sswitch_4db
    const-string v2, "layout/dialog_student_capture_student_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2713
    const v0, 0x7f0c00ba

    return v0

    .line 2700
    :sswitch_4e7
    const-string v2, "layout/fragment_classroom_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2701
    const v0, 0x7f0c00e7

    return v0

    .line 3096
    :sswitch_4f3
    const-string v2, "layout/fragment_school_detail_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3097
    const v0, 0x7f0c0125

    return v0

    .line 3066
    :sswitch_4ff
    const-string v2, "layout/layout_create_account_splash_page_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3067
    const v0, 0x7f0c01a7

    return v0

    .line 3588
    :sswitch_50b
    const-string v2, "layout/story_post_created_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3589
    const v0, 0x7f0c01f6

    return v0

    .line 2736
    :sswitch_517
    const-string v2, "layout-sw600dp/fragment_student_capture_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2737
    return v12

    .line 3864
    :sswitch_520
    const-string v2, "layout/item_class_code_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3865
    const v0, 0x7f0c018f

    return v0

    .line 2706
    :sswitch_52c
    const-string v2, "layout/fragment_add_school_student_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2707
    const v0, 0x7f0c00d4

    return v0

    .line 3162
    :sswitch_538
    const-string v2, "layout/activity_student_settings_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3163
    const v0, 0x7f0c0068

    return v0

    .line 3930
    :sswitch_544
    const-string v2, "layout/item_add_student_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3931
    const v0, 0x7f0c018c

    return v0

    .line 3504
    :sswitch_550
    const-string v2, "layout/activity_debug_urls_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3505
    const v0, 0x7f0c0033

    return v0

    .line 3456
    :sswitch_55c
    const-string v2, "layout/layout_student_connections_initial_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3457
    const v0, 0x7f0c01b0

    return v0

    .line 3744
    :sswitch_568
    const-string v2, "layout/fragment_combined_camera_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3745
    const v0, 0x7f0c00eb

    return v0

    .line 3252
    :sswitch_574
    const-string v2, "layout/fragment_combined_drawing_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3253
    const v0, 0x7f0c00ec

    return v0

    .line 3000
    :sswitch_580
    const-string v2, "layout/fragment_teacher_connection_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3001
    const v0, 0x7f0c017f

    return v0

    .line 2760
    :sswitch_58c
    const-string v2, "layout/fragment_school_search_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2761
    const v0, 0x7f0c0133

    return v0

    .line 4380
    :sswitch_598
    const-string v2, "layout/activity_email_verified_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4381
    const v0, 0x7f0c0037

    return v0

    .line 4248
    :sswitch_5a4
    const-string v2, "layout/dialog_teacher_invitation_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4249
    const v0, 0x7f0c00bd

    return v0

    .line 4440
    :sswitch_5b0
    const-string v2, "layout/view_drawer_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4441
    return v7

    .line 3084
    :sswitch_5b9
    const-string v2, "layout/dialog_password_changed_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3085
    const v0, 0x7f0c00b4

    return v0

    .line 3636
    :sswitch_5c5
    const-string v2, "layout/activity_student_capture_home_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3637
    return v10

    .line 4188
    :sswitch_5ce
    const-string v2, "layout/fragment_invite_item_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4189
    const v0, 0x7f0c0101

    return v0

    .line 4422
    :sswitch_5da
    const-string v2, "layout/fragment_parent_setup_student_account_ack_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4423
    const v0, 0x7f0c0119

    return v0

    .line 3972
    :sswitch_5e6
    const-string v2, "layout/fragment_teacher_story_feed_approval_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3973
    const v0, 0x7f0c0183

    return v0

    .line 3786
    :sswitch_5f2
    const-string v2, "layout/fragment_class_list_item_school_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3787
    const v0, 0x7f0c00e3

    return v0

    .line 3570
    :sswitch_5fe
    const-string v2, "layout/dialog_teacher_new_class_success_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3571
    const v0, 0x7f0c00be

    return v0

    .line 3546
    :sswitch_60a
    const-string v2, "layout/activity_share_media_content_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3547
    const v0, 0x7f0c005b

    return v0

    .line 4314
    :sswitch_616
    const-string v2, "layout/fragment_tab_notification_pending_posts_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4315
    const v0, 0x7f0c0178

    return v0

    .line 3210
    :sswitch_622
    const-string v2, "layout-sw600dp-land/activity_teacher_home_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3211
    return v6

    .line 4110
    :sswitch_62b
    const-string v2, "layout/fragment_onboarding_sign_up_title_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4111
    const v0, 0x7f0c010d

    return v0

    .line 3204
    :sswitch_637
    const-string v2, "layout/fragment_tab_class_wall_item_invite_card_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3205
    const v0, 0x7f0c0164

    return v0

    .line 2838
    :sswitch_643
    const-string v2, "layout/fragment_student_report_charts_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2839
    const v0, 0x7f0c0154

    return v0

    .line 4080
    :sswitch_64f
    const-string v2, "layout-sw600dp/fragment_student_login_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4081
    return v5

    .line 4224
    :sswitch_658
    const-string v2, "layout/toolbar_text_post_layout_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4225
    const v0, 0x7f0c020c

    return v0

    .line 3186
    :sswitch_664
    const-string v2, "layout/activity_teacher_home_drawer_header_light_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3187
    const v0, 0x7f0c006d

    return v0

    .line 3156
    :sswitch_670
    const-string v2, "layout/fragment_combined_camera_controls_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3157
    const v0, 0x7f0c00ea

    return v0

    .line 2928
    :sswitch_67c
    const-string v2, "layout/binding_variable_placeholder_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2929
    const v0, 0x7f0c0077

    return v0

    .line 3078
    :sswitch_688
    const-string v2, "layout/add_note_undo_popup_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3079
    const v0, 0x7f0c0076

    return v0

    .line 3804
    :sswitch_694
    const-string v2, "layout/item_student_drawing_tool_sticker_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3805
    const v0, 0x7f0c01a2

    return v0

    .line 4344
    :sswitch_6a0
    const-string v2, "layout/activity_onboarding_parent_sign_up_flow_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4345
    const v0, 0x7f0c0041

    return v0

    .line 3228
    :sswitch_6ac
    const-string v2, "layout/fragment_add_coteacher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3229
    const v0, 0x7f0c00cf

    return v0

    .line 3954
    :sswitch_6b8
    const-string v2, "layout/fragment_teacher_search_item_empty_teacher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3955
    const v0, 0x7f0c0180

    return v0

    .line 4446
    :sswitch_6c4
    const-string v2, "layout-sw600dp-land/view_drawer_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4447
    return v7

    .line 3462
    :sswitch_6cd
    const-string v2, "layout-sw600dp/fragment_dojo_camera_controls_270_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3463
    const v0, 0x7f0c00f3

    return v0

    .line 3996
    :sswitch_6d9
    const-string v2, "layout-sw600dp/fragment_dojo_camera_controls_180_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3997
    const v0, 0x7f0c00f2

    return v0

    .line 3114
    :sswitch_6e5
    const-string v2, "layout/fragment_parent_sign_up_credentials_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3115
    const v0, 0x7f0c011b

    return v0

    .line 3924
    :sswitch_6f1
    const-string v2, "layout/fragment_student_avatar_editor_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3925
    const v0, 0x7f0c013e

    return v0

    .line 4092
    :sswitch_6fd
    const-string v2, "layout/fragment_teacher_welcome_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4093
    const v0, 0x7f0c0186

    return v0

    .line 3408
    :sswitch_709
    const-string v2, "layout/fragment_teacher_student_connection_text_codes_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3409
    const v0, 0x7f0c0185

    return v0

    .line 4458
    :sswitch_715
    const-string v2, "layout/fragment_student_capture_story_feed_item_participant_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4459
    const v0, 0x7f0c0146

    return v0

    .line 2880
    :sswitch_721
    const-string v2, "layout/fragment_student_relation_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2881
    const v0, 0x7f0c0151

    return v0

    .line 3270
    :sswitch_72d
    const-string v2, "layout/fragment_hold_tight_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3271
    const v0, 0x7f0c0100

    return v0

    .line 2976
    :sswitch_739
    const-string v2, "layout/layout_parent_connections_invite_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2977
    const v0, 0x7f0c01ac

    return v0

    .line 4152
    :sswitch_745
    const-string v2, "layout/layout_legacy_video_view_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4153
    const v0, 0x7f0c01a9

    return v0

    .line 3294
    :sswitch_751
    const-string v2, "layout/fragment_combined_camera_compose_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3295
    const v0, 0x7f0c00e9

    return v0

    .line 2874
    :sswitch_75d
    const-string v2, "layout/setup_skills_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2875
    const v0, 0x7f0c01f3

    return v0

    .line 3174
    :sswitch_769
    const-string v2, "layout/fragment_onboarding_sign_up_email_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3175
    const v0, 0x7f0c010b

    return v0

    .line 3138
    :sswitch_775
    const-string v2, "layout/combined_compose_audience_student_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3139
    const v0, 0x7f0c0088

    return v0

    .line 3498
    :sswitch_781
    const-string v2, "layout/dialog_add_student_find_teacher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3499
    const v0, 0x7f0c00a4

    return v0

    .line 3246
    :sswitch_78d
    const-string v2, "layout/fragment_preview_message_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3247
    const v0, 0x7f0c011e

    return v0

    .line 3048
    :sswitch_799
    const-string v2, "layout/fragment_single_notification_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3049
    const v0, 0x7f0c0139

    return v0

    .line 3414
    :sswitch_7a5
    const-string v2, "layout/fragment_edit_students_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3415
    const v0, 0x7f0c00fa

    return v0

    .line 3012
    :sswitch_7b1
    const-string v2, "layout/fragment_tab_class_wall_item_compose_large_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3013
    const v0, 0x7f0c0160

    return v0

    .line 4218
    :sswitch_7bd
    const-string v2, "layout/fragment_leader_sign_up_role_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4219
    const v0, 0x7f0c0103

    return v0

    .line 2820
    :sswitch_7c9
    const-string v2, "layout/fragment_class_code_student_select_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2821
    const v0, 0x7f0c00e0

    return v0

    .line 4164
    :sswitch_7d5
    const-string v2, "layout/fragment_school_search_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4165
    const v0, 0x7f0c0135

    return v0

    .line 3900
    :sswitch_7e1
    const-string v2, "layout/toolbar_teacher_onboarding_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3901
    const v0, 0x7f0c020a

    return v0

    .line 3828
    :sswitch_7ed
    const-string v2, "layout/fragment_school_directory_item_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3829
    const v0, 0x7f0c012a

    return v0

    .line 2748
    :sswitch_7f9
    const-string v2, "layout-sw600dp-land/fragment_student_capture_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2749
    return v12

    .line 4068
    :sswitch_802
    const-string v2, "layout/fragment_parent_setup_student_account_qr_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4069
    const v0, 0x7f0c011a

    return v0

    .line 3960
    :sswitch_80e
    const-string v2, "layout/fragment_student_registration_form_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3961
    const v0, 0x7f0c0150

    return v0

    .line 2688
    :sswitch_81a
    const-string v2, "layout/activity_setup_skills_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2689
    const v0, 0x7f0c0057

    return v0

    .line 4236
    :sswitch_826
    const-string v2, "layout/dialog_exit_student_mode_password_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4237
    const v0, 0x7f0c00ae

    return v0

    .line 3678
    :sswitch_832
    const-string v2, "layout/fragment_story_share_to_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3679
    const v0, 0x7f0c013a

    return v0

    .line 3348
    :sswitch_83e
    const-string v2, "layout/fragment_seen_by_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3349
    const v0, 0x7f0c0136

    return v0

    .line 2826
    :sswitch_84a
    const-string v2, "layout/fragment_add_edit_behaviours_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2827
    const v0, 0x7f0c00d0

    return v0

    .line 3558
    :sswitch_856
    const-string v2, "layout/fragment_story_share_to_student_header_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3559
    const v0, 0x7f0c013d

    return v0

    .line 3366
    :sswitch_862
    const-string v2, "layout/fragment_setup_skills_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3367
    const v0, 0x7f0c0138

    return v0

    .line 2778
    :sswitch_86e
    const-string v2, "layout/fragment_dojo_camera_tooltip_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2779
    const v0, 0x7f0c00f5

    return v0

    .line 4284
    :sswitch_87a
    const-string v2, "layout/fragment_school_search_adapter_footer_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4285
    const v0, 0x7f0c0134

    return v0

    .line 3042
    :sswitch_886
    const-string v2, "layout/fragment_account_switcher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3043
    const v0, 0x7f0c00c9

    return v0

    .line 2850
    :sswitch_892
    const-string v2, "layout/activity_share_media_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2851
    const v0, 0x7f0c0059

    return v0

    .line 4434
    :sswitch_89e
    const-string v2, "layout/fragment_student_capture_home_story_feed_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4435
    const v0, 0x7f0c013f

    return v0

    .line 4308
    :sswitch_8aa
    const-string v2, "layout/connection_type_header_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4309
    const v0, 0x7f0c0089

    return v0

    .line 2904
    :sswitch_8b6
    const-string v2, "layout-sw600dp/fragment_dojo_camera_controls_90_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2905
    const v0, 0x7f0c00f4

    return v0

    .line 3030
    :sswitch_8c2
    const-string v2, "layout/dialog_enter_student_mode_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3031
    const v0, 0x7f0c00ad

    return v0

    .line 3324
    :sswitch_8ce
    const-string v2, "layout/debug_feature_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3325
    const v0, 0x7f0c0090

    return v0

    .line 3342
    :sswitch_8da
    const-string v2, "layout/fragment_add_edit_class_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3343
    const v0, 0x7f0c00d1

    return v0

    .line 4212
    :sswitch_8e6
    const-string v2, "layout/fragment_push_notifications_settings_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4213
    const v0, 0x7f0c0121

    return v0

    .line 4278
    :sswitch_8f2
    const-string v2, "layout/fragment_scheduled_message_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4279
    const v0, 0x7f0c0122

    return v0

    .line 3618
    :sswitch_8fe
    const-string v2, "layout-land/fragment_add_class_poster_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3619
    return v4

    .line 3354
    :sswitch_907
    const-string v2, "layout-sw600dp/fragment_mark_students_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3355
    return v3

    .line 3600
    :sswitch_910
    const-string v2, "layout/fragment_tab_class_wall_item_student_report_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3601
    const v0, 0x7f0c016f

    return v0

    .line 3510
    :sswitch_91c
    const-string v2, "layout/fragment_tab_class_wall_item_invite_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3511
    const v0, 0x7f0c0163

    return v0

    .line 2862
    :sswitch_928
    const-string v2, "layout/fragment_student_capture_mark_students_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2863
    const v0, 0x7f0c0141

    return v0

    .line 2952
    :sswitch_934
    const-string v2, "layout/fragment_seen_by_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2953
    const v0, 0x7f0c0137

    return v0

    .line 3882
    :sswitch_940
    const-string v2, "layout/activity_class_link_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3883
    const v0, 0x7f0c0026

    return v0

    .line 2742
    :sswitch_94c
    const-string v2, "layout/fragment_student_capture_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2743
    return v12

    .line 3132
    :sswitch_955
    const-string v3, "layout/fragment_student_capture_story_feed_item_0"

    invoke-virtual {p1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_e05

    .line 3133
    return v2

    .line 2916
    :sswitch_95e
    const-string v2, "layout/fragment_school_directory_item_teacher_pending_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2917
    const v0, 0x7f0c012f

    return v0

    .line 3192
    :sswitch_96a
    const-string v2, "layout/fragment_student_connections_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3193
    const v0, 0x7f0c014c

    return v0

    .line 2784
    :sswitch_976
    const-string v2, "layout/activity_add_edit_class_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2785
    const v0, 0x7f0c001d

    return v0

    .line 3624
    :sswitch_982
    const-string v2, "layout/fragment_add_class_poster_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3625
    return v4

    .line 4242
    :sswitch_98b
    const-string v2, "layout/fragment_school_directory_item_student_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4243
    const v0, 0x7f0c012b

    return v0

    .line 4368
    :sswitch_997
    const-string v2, "layout/item_class_code_student_select_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4369
    const v0, 0x7f0c0190

    return v0

    .line 2814
    :sswitch_9a3
    const-string v2, "layout/dialog_student_age_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2815
    const v0, 0x7f0c00b9

    return v0

    .line 3150
    :sswitch_9af
    const-string v2, "layout/layout_dojo_video_view_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3151
    const v0, 0x7f0c01a8

    return v0

    .line 3948
    :sswitch_9bb
    const-string v2, "layout/layout_student_list_empty_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3949
    const v0, 0x7f0c01b3

    return v0

    .line 3810
    :sswitch_9c7
    const-string v2, "layout/fragment_student_report_selector_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3811
    const v0, 0x7f0c0158

    return v0

    .line 4158
    :sswitch_9d3
    const-string v2, "layout/dialog_parent_invitation_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4159
    const v0, 0x7f0c00b2

    return v0

    .line 4452
    :sswitch_9df
    const-string v2, "layout/fragment_school_directory_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4453
    const v0, 0x7f0c0129

    return v0

    .line 2790
    :sswitch_9eb
    const-string v2, "layout/fragment_login_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2791
    const v0, 0x7f0c0104

    return v0

    .line 3984
    :sswitch_9f7
    const-string v2, "layout/fragment_tab_story_feed_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3985
    return v9

    .line 4014
    :sswitch_a00
    const-string v2, "layout/fragment_add_class_invite_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4015
    const v0, 0x7f0c00cc

    return v0

    .line 4350
    :sswitch_a0c
    const-string v2, "layout/item_parent_checklist_complete_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4351
    const v0, 0x7f0c019d

    return v0

    .line 2808
    :sswitch_a18
    const-string v2, "layout/parent_checklist_success_overlay_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2809
    const v0, 0x7f0c01de

    return v0

    .line 3690
    :sswitch_a24
    const-string v2, "layout/invite_parent_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3691
    const v0, 0x7f0c018a

    return v0

    .line 3318
    :sswitch_a30
    const-string v2, "layout/fragment_award_grid_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3319
    const v0, 0x7f0c00da

    return v0

    .line 2766
    :sswitch_a3c
    const-string v2, "layout/fragment_tab_class_wall_item_webview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2767
    const v0, 0x7f0c0170

    return v0

    .line 3198
    :sswitch_a48
    const-string v2, "layout/placeholder_empty_with_refresh_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3199
    const v0, 0x7f0c01e5

    return v0

    .line 4428
    :sswitch_a54
    const-string v2, "layout/fragment_account_switcher_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4429
    const v0, 0x7f0c00cb

    return v0

    .line 2664
    :sswitch_a60
    const-string v2, "layout/dialog_avatar_grid_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2665
    const v0, 0x7f0c00a6

    return v0

    .line 2958
    :sswitch_a6c
    const-string v2, "layout/toolbar_text_layout_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2959
    const v0, 0x7f0c020b

    return v0

    .line 4404
    :sswitch_a78
    const-string v2, "layout/fragment_add_edit_group_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4405
    const v0, 0x7f0c00d2

    return v0

    .line 2940
    :sswitch_a84
    const-string v2, "layout/fragment_school_null_state_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2941
    const v0, 0x7f0c0132

    return v0

    .line 4020
    :sswitch_a90
    const-string v2, "layout/fragment_create_account_splash_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4021
    const v0, 0x7f0c00ee

    return v0

    .line 3756
    :sswitch_a9c
    const-string v2, "layout/fragment_onboarding_enable_camera_primer_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3757
    const v0, 0x7f0c0108

    return v0

    .line 3528
    :sswitch_aa8
    const-string v2, "layout/fragment_change_password_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3529
    const v0, 0x7f0c00dc

    return v0

    .line 3660
    :sswitch_ab4
    const-string v2, "layout/fragment_onboarding_sign_up_parent_anti_abuse_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3661
    const v0, 0x7f0c010c

    return v0

    .line 3486
    :sswitch_ac0
    const-string v2, "layout/layout_parent_connections_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3487
    const v0, 0x7f0c01ad

    return v0

    .line 4362
    :sswitch_acc
    const-string v2, "layout/fragment_tab_student_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4363
    const v0, 0x7f0c017b

    return v0

    .line 4170
    :sswitch_ad8
    const-string v2, "layout/fragment_meet_teacher_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4171
    const v0, 0x7f0c0106

    return v0

    .line 2898
    :sswitch_ae4
    const-string v2, "layout/fragment_tab_class_wall_generic_button_icon_text_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2899
    const v0, 0x7f0c015a

    return v0

    .line 3714
    :sswitch_af0
    const-string v2, "layout/item_parent_checklist_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3715
    const v0, 0x7f0c019c

    return v0

    .line 3594
    :sswitch_afc
    const-string v2, "layout/toolbar_teacher_approval_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3595
    const v0, 0x7f0c0208

    return v0

    .line 3840
    :sswitch_b08
    const-string v2, "layout/fragment_school_directory_item_teacher_request_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3841
    const v0, 0x7f0c0130

    return v0

    .line 3708
    :sswitch_b14
    const-string v2, "layout/fragment_chat_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3709
    const v0, 0x7f0c00dd

    return v0

    .line 3438
    :sswitch_b20
    const-string v2, "layout/fragment_tab_notification_list_pending_requests_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3439
    const v0, 0x7f0c0177

    return v0

    .line 3552
    :sswitch_b2c
    const-string v2, "layout/dialog_change_name_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3553
    const v0, 0x7f0c00a8

    return v0

    .line 2844
    :sswitch_b38
    const-string v2, "layout/fragment_student_report_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2845
    const v0, 0x7f0c0155

    return v0

    .line 4296
    :sswitch_b44
    const-string v2, "layout/fragment_award_pager_dialog_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4297
    const v0, 0x7f0c00db

    return v0

    .line 3216
    :sswitch_b50
    const-string v2, "layout/activity_teacher_home_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3217
    return v6

    .line 3780
    :sswitch_b59
    const-string v2, "layout/chat_empty_broadcasts_view_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3781
    const v0, 0x7f0c007f

    return v0

    .line 2910
    :sswitch_b65
    const-string v2, "layout/activity_teacher_home_drawer_header_dark_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2911
    const v0, 0x7f0c006c

    return v0

    .line 3738
    :sswitch_b71
    const-string v2, "layout/fragment_student_profile_date_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3739
    const v0, 0x7f0c014f

    return v0

    .line 3822
    :sswitch_b7d
    const-string v2, "layout/fragment_student_codes_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3823
    const v0, 0x7f0c014a

    return v0

    .line 4146
    :sswitch_b89
    const-string v2, "layout/fragment_parent_channel_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4147
    const v0, 0x7f0c0112

    return v0

    .line 2892
    :sswitch_b95
    const-string v2, "layout/fragment_onboarding_splash_user_role_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2893
    const v0, 0x7f0c010e

    return v0

    .line 4326
    :sswitch_ba1
    const-string v2, "layout/fragment_push_notifications_quiet_hours_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4327
    const v0, 0x7f0c0120

    return v0

    .line 3612
    :sswitch_bad
    const-string v2, "layout/fragment_class_list_item_teacher_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3613
    const v0, 0x7f0c00e4

    return v0

    .line 3168
    :sswitch_bb9
    const-string v2, "layout/fragment_tab_notification_list_item_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3169
    const v0, 0x7f0c0176

    return v0

    .line 4062
    :sswitch_bc5
    const-string v2, "layout/view_list_header_limit_width_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4063
    const v0, 0x7f0c0214

    return v0

    .line 2994
    :sswitch_bd1
    const-string v2, "layout/activity_qrcode_scan_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2995
    const v0, 0x7f0c0052

    return v0

    .line 3180
    :sswitch_bdd
    const-string v2, "layout/activity_share_media_audience_selector_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3181
    const v0, 0x7f0c005a

    return v0

    .line 3720
    :sswitch_be9
    const-string v2, "layout/fragment_student_report_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3721
    const v0, 0x7f0c0157

    return v0

    .line 3918
    :sswitch_bf5
    const-string v2, "layout/student_login_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3919
    return v8

    .line 4266
    :sswitch_bfe
    const-string v2, "layout/activity_school_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4267
    const v0, 0x7f0c0054

    return v0

    .line 3672
    :sswitch_c0a
    const-string v2, "layout/fragment_parent_school_search_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3673
    const v0, 0x7f0c0118

    return v0

    .line 4074
    :sswitch_c16
    const-string v2, "layout/fragment_student_login_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4075
    return v5

    .line 4260
    :sswitch_c1f
    const-string v2, "layout/fragment_parent_connections_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4261
    const v0, 0x7f0c0116

    return v0

    .line 4086
    :sswitch_c2b
    const-string v2, "layout/fragment_parent_account_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4087
    const v0, 0x7f0c0110

    return v0

    .line 3234
    :sswitch_c37
    const-string v2, "layout/fragment_teacher_approval_feed_item_text_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3235
    const v0, 0x7f0c017d

    return v0

    .line 3126
    :sswitch_c43
    const-string v3, "layout-sw600dp-land/fragment_student_capture_story_feed_item_0"

    invoke-virtual {p1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_e05

    .line 3127
    return v2

    .line 2694
    :sswitch_c4c
    const-string v2, "layout/fragment_student_drawing_tool_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2695
    const v0, 0x7f0c014d

    return v0

    .line 3492
    :sswitch_c58
    const-string v2, "layout/parent_connections_single_code_invite_section_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3493
    const v0, 0x7f0c01e0

    return v0

    .line 4038
    :sswitch_c64
    const-string v2, "layout/fragment_tab_class_wall_item_demo_empty_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4039
    const v0, 0x7f0c0162

    return v0

    .line 3300
    :sswitch_c70
    const-string v2, "layout/toolbar_base_layout_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3301
    const v0, 0x7f0c0200

    return v0

    .line 3792
    :sswitch_c7c
    const-string v2, "layout/fragment_award_grid_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3793
    const v0, 0x7f0c00d9

    return v0

    .line 4122
    :sswitch_c88
    const-string v2, "layout/dialog_student_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4123
    const v0, 0x7f0c00bc

    return v0

    .line 4392
    :sswitch_c94
    const-string v2, "layout/view_drawing_tool_discard_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4393
    const v0, 0x7f0c0211

    return v0

    .line 3648
    :sswitch_ca0
    const-string v2, "layout/fragment_tab_class_wall_item_student_permission_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3649
    const v0, 0x7f0c016e

    return v0

    .line 3642
    :sswitch_cac
    const-string v2, "layout/fragment_parent_add_class_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3643
    const v0, 0x7f0c0111

    return v0

    .line 3306
    :sswitch_cb8
    const-string v2, "layout/activity_debug_deeplinks_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3307
    const v0, 0x7f0c0032

    return v0

    .line 3378
    :sswitch_cc4
    const-string v2, "layout/fragment_parent_checklist_invite_family_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3379
    const v0, 0x7f0c0114

    return v0

    .line 3444
    :sswitch_cd0
    const-string v2, "layout/fragment_school_directory_item_student_add_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3445
    const v0, 0x7f0c012c

    return v0

    .line 3870
    :sswitch_cdc
    const-string v2, "layout/toolbar_onboarding_progress_layout_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3871
    const v0, 0x7f0c0205

    return v0

    .line 3852
    :sswitch_ce8
    const-string v2, "layout/fragment_tab_notification_list_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3853
    const v0, 0x7f0c0174

    return v0

    .line 2964
    :sswitch_cf4
    const-string v2, "layout/invite_list_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2965
    const v0, 0x7f0c0189

    return v0

    .line 3360
    :sswitch_d00
    const-string v2, "layout/fragment_mark_students_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3361
    return v3

    .line 4416
    :sswitch_d09
    const-string v2, "layout/fragment_video_preview_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4417
    const v0, 0x7f0c0187

    return v0

    .line 3906
    :sswitch_d15
    const-string v2, "layout/parent_connections_individual_codes_invite_section_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3907
    const v0, 0x7f0c01df

    return v0

    .line 2934
    :sswitch_d21
    const-string v2, "layout/fragment_student_report_list_dialog_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2935
    const v0, 0x7f0c0156

    return v0

    .line 3942
    :sswitch_d2d
    const-string v2, "layout/dialog_parent_delete_connection_request_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3943
    const v0, 0x7f0c00b1

    return v0

    .line 4056
    :sswitch_d39
    const-string v2, "layout/fragment_dojo_camera_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4057
    const v0, 0x7f0c00f0

    return v0

    .line 3480
    :sswitch_d45
    const-string v2, "layout/combined_compose_audience_group_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3481
    const v0, 0x7f0c0086

    return v0

    .line 3474
    :sswitch_d51
    const-string v2, "layout/item_grid_teacher_title_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3475
    const v0, 0x7f0c0192

    return v0

    .line 3816
    :sswitch_d5d
    const-string v2, "layout/student_login_list_not_my_class_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3817
    const v0, 0x7f0c01fa

    return v0

    .line 3702
    :sswitch_d69
    const-string v2, "layout/activity_student_search_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3703
    const v0, 0x7f0c0067

    return v0

    .line 4302
    :sswitch_d75
    const-string v2, "layout/fragment_forgot_password_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4303
    const v0, 0x7f0c00fc

    return v0

    .line 3846
    :sswitch_d81
    const-string v2, "layout/view_students_moved_tooltip_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3847
    const v0, 0x7f0c0218

    return v0

    .line 3120
    :sswitch_d8d
    const-string v2, "layout/fragment_student_code_form_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3121
    const v0, 0x7f0c0149

    return v0

    .line 3072
    :sswitch_d99
    const-string v2, "layout/fragment_message_recipients_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3073
    const v0, 0x7f0c0107

    return v0

    .line 3336
    :sswitch_da5
    const-string v2, "layout/fragment_attendance_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3337
    const v0, 0x7f0c00d6

    return v0

    .line 4194
    :sswitch_db1
    const-string v2, "layout/fragment_web_view_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4195
    const v0, 0x7f0c0188

    return v0

    .line 2676
    :sswitch_dbd
    const-string v2, "layout/view_teacher_student_connection_text_codes_instructions_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2677
    const v0, 0x7f0c0219

    return v0

    .line 2802
    :sswitch_dc9
    const-string v2, "layout/view_list_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 2803
    const v0, 0x7f0c0213

    return v0

    .line 3774
    :sswitch_dd5
    const-string v2, "layout/activity_parent_checklist_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3775
    const v0, 0x7f0c0046

    return v0

    .line 4206
    :sswitch_de1
    const-string v2, "layout/thumbnail_item_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 4207
    const v0, 0x7f0c01fe

    return v0

    .line 3420
    :sswitch_ded
    const-string v2, "layout/fragment_add_class_invite_code_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3421
    const v0, 0x7f0c00cd

    return v0

    .line 3144
    :sswitch_df9
    const-string v2, "layout/item_onboarding_class_code_header_0"

    invoke-virtual {p1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_e05

    .line 3145
    const v0, 0x7f0c019b

    return v0

    .line 4470
    :cond_e05
    :goto_e05
    return v0

    :sswitch_data_e06
    .sparse-switch
        -0x7ff1b754 -> :sswitch_df9
        -0x7f0c897b -> :sswitch_ded
        -0x7d0aa704 -> :sswitch_de1
        -0x7c6be673 -> :sswitch_dd5
        -0x7c3fe396 -> :sswitch_dc9
        -0x7becf608 -> :sswitch_dbd
        -0x7b0a44b5 -> :sswitch_db1
        -0x797d3b3c -> :sswitch_da5
        -0x787ac9b3 -> :sswitch_d99
        -0x770745a7 -> :sswitch_d8d
        -0x7647340a -> :sswitch_d81
        -0x75d10002 -> :sswitch_d75
        -0x753a0a98 -> :sswitch_d69
        -0x74899310 -> :sswitch_d5d
        -0x73a86b46 -> :sswitch_d51
        -0x73629603 -> :sswitch_d45
        -0x70a04545 -> :sswitch_d39
        -0x70067ff0 -> :sswitch_d2d
        -0x6fff2663 -> :sswitch_d21
        -0x6c14d67b -> :sswitch_d15
        -0x6b962df5 -> :sswitch_d09
        -0x6b5b74af -> :sswitch_d00
        -0x6b5a686c -> :sswitch_cf4
        -0x6ae98fb1 -> :sswitch_ce8
        -0x6a5398ee -> :sswitch_cdc
        -0x69baa2cb -> :sswitch_cd0
        -0x68770d73 -> :sswitch_cc4
        -0x67f67859 -> :sswitch_cb8
        -0x67a2ada0 -> :sswitch_cac
        -0x6629c3ca -> :sswitch_ca0
        -0x660dcd62 -> :sswitch_c94
        -0x660a05fb -> :sswitch_c88
        -0x619ba5fd -> :sswitch_c7c
        -0x5fe088e0 -> :sswitch_c70
        -0x5fdd67ac -> :sswitch_c64
        -0x5f10a74b -> :sswitch_c58
        -0x5b084708 -> :sswitch_c4c
        -0x5ad2035f -> :sswitch_c43
        -0x59e896a2 -> :sswitch_c37
        -0x596de34d -> :sswitch_c2b
        -0x59554ca5 -> :sswitch_c1f
        -0x5868dbad -> :sswitch_c16
        -0x581e0245 -> :sswitch_c0a
        -0x57f09e50 -> :sswitch_bfe
        -0x578acaba -> :sswitch_bf5
        -0x57679658 -> :sswitch_be9
        -0x56d6ec3b -> :sswitch_bdd
        -0x547e23ec -> :sswitch_bd1
        -0x53bb8353 -> :sswitch_bc5
        -0x528bcbb7 -> :sswitch_bb9
        -0x524316c9 -> :sswitch_bad
        -0x5207f75d -> :sswitch_ba1
        -0x51b92ac3 -> :sswitch_b95
        -0x513d656a -> :sswitch_b89
        -0x50e9d457 -> :sswitch_b7d
        -0x5077865d -> :sswitch_b71
        -0x501cc71d -> :sswitch_b65
        -0x4fb86b71 -> :sswitch_b59
        -0x4e4358e8 -> :sswitch_b50
        -0x4e1aab7f -> :sswitch_b44
        -0x4ded10bf -> :sswitch_b38
        -0x4ca00607 -> :sswitch_b2c
        -0x4c1d08ea -> :sswitch_b20
        -0x4b74d30d -> :sswitch_b14
        -0x4b6090f6 -> :sswitch_b08
        -0x4b532106 -> :sswitch_afc
        -0x48d78f77 -> :sswitch_af0
        -0x476c2da5 -> :sswitch_ae4
        -0x473cf7b1 -> :sswitch_ad8
        -0x470ff9d9 -> :sswitch_acc
        -0x464a89e2 -> :sswitch_ac0
        -0x4645e18d -> :sswitch_ab4
        -0x44ce1aaf -> :sswitch_aa8
        -0x43e66da4 -> :sswitch_a9c
        -0x4373979d -> :sswitch_a90
        -0x4362a775 -> :sswitch_a84
        -0x4350d07d -> :sswitch_a78
        -0x431e5fbc -> :sswitch_a6c
        -0x4265e5d5 -> :sswitch_a60
        -0x4116179a -> :sswitch_a54
        -0x40f419aa -> :sswitch_a48
        -0x3ec87248 -> :sswitch_a3c
        -0x3ec71fef -> :sswitch_a30
        -0x3d93bcff -> :sswitch_a24
        -0x3c7a38a4 -> :sswitch_a18
        -0x3b64498f -> :sswitch_a0c
        -0x3b4ec057 -> :sswitch_a00
        -0x3afafbd3 -> :sswitch_9f7
        -0x3acbc1d0 -> :sswitch_9eb
        -0x3a661c83 -> :sswitch_9df
        -0x3a3c34f3 -> :sswitch_9d3
        -0x39befe39 -> :sswitch_9c7
        -0x392524cf -> :sswitch_9bb
        -0x3848bc67 -> :sswitch_9af
        -0x35732b06 -> :sswitch_9a3
        -0x348d9d2b -> :sswitch_997
        -0x3406792d -> :sswitch_98b
        -0x33ca7313 -> :sswitch_982
        -0x337c52a3 -> :sswitch_976
        -0x2f89dc68 -> :sswitch_96a
        -0x2f689bee -> :sswitch_95e
        -0x2b6cfc6c -> :sswitch_955
        -0x2aed7d6e -> :sswitch_94c
        -0x297fd423 -> :sswitch_940
        -0x28b29bee -> :sswitch_934
        -0x27f3f1a0 -> :sswitch_928
        -0x266d0594 -> :sswitch_91c
        -0x24f9cea5 -> :sswitch_910
        -0x24dcc0a0 -> :sswitch_907
        -0x2401f98f -> :sswitch_8fe
        -0x22c17f88 -> :sswitch_8f2
        -0x22582ada -> :sswitch_8e6
        -0x21ecf7c4 -> :sswitch_8da
        -0x21b5e195 -> :sswitch_8ce
        -0x218e1b6f -> :sswitch_8c2
        -0x2120b3b5 -> :sswitch_8b6
        -0x20672573 -> :sswitch_8aa
        -0x1f9a342e -> :sswitch_89e
        -0x1f934e76 -> :sswitch_892
        -0x1d954f72 -> :sswitch_886
        -0x1d93b6ae -> :sswitch_87a
        -0x1d7a1981 -> :sswitch_86e
        -0x1bdf8f21 -> :sswitch_862
        -0x1b42ca7e -> :sswitch_856
        -0x1b020fac -> :sswitch_84a
        -0x19e4669e -> :sswitch_83e
        -0x196ed340 -> :sswitch_832
        -0x18f62b4b -> :sswitch_826
        -0x18f615c0 -> :sswitch_81a
        -0x187e6ff3 -> :sswitch_80e
        -0x1837990b -> :sswitch_802
        -0x18162761 -> :sswitch_7f9
        -0x1711ad89 -> :sswitch_7ed
        -0x16e040ae -> :sswitch_7e1
        -0x166f92a6 -> :sswitch_7d5
        -0x14d1edee -> :sswitch_7c9
        -0x12830e6b -> :sswitch_7bd
        -0x126b47b3 -> :sswitch_7b1
        -0x11ae6340 -> :sswitch_7a5
        -0x11a7be77 -> :sswitch_799
        -0xf41cfe9 -> :sswitch_78d
        -0xf27cc5f -> :sswitch_781
        -0xed1377f -> :sswitch_775
        -0xe96e4cf -> :sswitch_769
        -0xdf0c371 -> :sswitch_75d
        -0xa513a07 -> :sswitch_751
        -0x8dc7480 -> :sswitch_745
        -0x86611f7 -> :sswitch_739
        -0x6f11a65 -> :sswitch_72d
        -0x609d307 -> :sswitch_721
        -0x5abf878 -> :sswitch_715
        -0x5a60405 -> :sswitch_709
        -0x5058fb4 -> :sswitch_6fd
        -0x46586aa -> :sswitch_6f1
        -0x394ee20 -> :sswitch_6e5
        -0x3638689 -> :sswitch_6d9
        -0x355e367 -> :sswitch_6cd
        -0x27153f6 -> :sswitch_6c4
        -0x2417edb -> :sswitch_6b8
        -0x1a6bb21 -> :sswitch_6ac
        -0xa7e8e3 -> :sswitch_6a0
        -0x9f812d -> :sswitch_694
        -0x25f114 -> :sswitch_688
        0x532a60 -> :sswitch_67c
        0x8dbe71 -> :sswitch_670
        0xc0778b -> :sswitch_664
        0xc87ed1 -> :sswitch_658
        0x1c1a1a4 -> :sswitch_64f
        0x1effd51 -> :sswitch_643
        0x2180e05 -> :sswitch_637
        0x52032ed -> :sswitch_62b
        0x97e972b -> :sswitch_622
        0xa18dd18 -> :sswitch_616
        0xcf6bd44 -> :sswitch_60a
        0xdddeeff -> :sswitch_5fe
        0xeff0381 -> :sswitch_5f2
        0xf5a5f64 -> :sswitch_5e6
        0x105c60f7 -> :sswitch_5da
        0x10a25a5e -> :sswitch_5ce
        0x11b526b8 -> :sswitch_5c5
        0x11fbb273 -> :sswitch_5b9
        0x13379bfd -> :sswitch_5b0
        0x14318b79 -> :sswitch_5a4
        0x156bd587 -> :sswitch_598
        0x1659e31a -> :sswitch_58c
        0x16ddaab6 -> :sswitch_580
        0x16f2887f -> :sswitch_574
        0x177c82af -> :sswitch_568
        0x18fd59f7 -> :sswitch_55c
        0x1a0ec90c -> :sswitch_550
        0x1b06fbe7 -> :sswitch_544
        0x1b375163 -> :sswitch_538
        0x1bdc8269 -> :sswitch_52c
        0x1c98d2a2 -> :sswitch_520
        0x1d26afe1 -> :sswitch_517
        0x1dc31e09 -> :sswitch_50b
        0x1dce4e53 -> :sswitch_4ff
        0x1fa78f83 -> :sswitch_4f3
        0x206b4f9a -> :sswitch_4e7
        0x20f0f2d2 -> :sswitch_4db
        0x21e07af7 -> :sswitch_4cf
        0x22e0b117 -> :sswitch_4c3
        0x23e149d2 -> :sswitch_4b7
        0x255cf0a4 -> :sswitch_4ab
        0x264fdb61 -> :sswitch_49f
        0x268102ba -> :sswitch_493
        0x27c27982 -> :sswitch_487
        0x294451ff -> :sswitch_47b
        0x2998f23a -> :sswitch_46f
        0x29fcb069 -> :sswitch_463
        0x2a7bdccb -> :sswitch_457
        0x2b45262e -> :sswitch_44b
        0x2c7f409f -> :sswitch_43f
        0x2f1d48f8 -> :sswitch_433
        0x2fff07b6 -> :sswitch_427
        0x3068c37f -> :sswitch_41b
        0x307f0c2a -> :sswitch_40f
        0x30f5c833 -> :sswitch_403
        0x3157654e -> :sswitch_3f7
        0x317dac8e -> :sswitch_3eb
        0x31c9069a -> :sswitch_3df
        0x31cd0317 -> :sswitch_3d6
        0x340a387b -> :sswitch_3ca
        0x340b3b80 -> :sswitch_3c1
        0x34a1113a -> :sswitch_3b5
        0x3614d945 -> :sswitch_3a9
        0x3746262b -> :sswitch_39d
        0x3781fd5a -> :sswitch_391
        0x37ba2393 -> :sswitch_385
        0x37f13ec0 -> :sswitch_379
        0x3851a28e -> :sswitch_36d
        0x3958b459 -> :sswitch_361
        0x3b02790e -> :sswitch_355
        0x3cf1f24d -> :sswitch_349
        0x419b53cb -> :sswitch_340
        0x41bf0f9d -> :sswitch_334
        0x41d8fb66 -> :sswitch_328
        0x42bf1dda -> :sswitch_31c
        0x4340426c -> :sswitch_310
        0x43e2954a -> :sswitch_304
        0x4435da19 -> :sswitch_2f8
        0x44c6cd9b -> :sswitch_2ec
        0x456fd6c5 -> :sswitch_2e0
        0x4785240d -> :sswitch_2d4
        0x478d8757 -> :sswitch_2c8
        0x48aa013a -> :sswitch_2bc
        0x4a437870 -> :sswitch_2b0
        0x4a718302 -> :sswitch_2a4
        0x4b329b9f -> :sswitch_298
        0x4b53687b -> :sswitch_28c
        0x4c1d1eac -> :sswitch_280
        0x4de98cd3 -> :sswitch_274
        0x4e062bf4 -> :sswitch_268
        0x4e2b0996 -> :sswitch_25c
        0x4f2fbb6d -> :sswitch_253
        0x4fbe49e6 -> :sswitch_247
        0x5035dc38 -> :sswitch_23b
        0x50baaeaf -> :sswitch_22f
        0x50c33b74 -> :sswitch_223
        0x51e01d5a -> :sswitch_217
        0x53571c73 -> :sswitch_20b
        0x5375779b -> :sswitch_1ff
        0x563e8572 -> :sswitch_1f3
        0x5741b64a -> :sswitch_1e7
        0x5982ef72 -> :sswitch_1db
        0x5983052a -> :sswitch_1cf
        0x59d8095e -> :sswitch_1c3
        0x5a510cd7 -> :sswitch_1b7
        0x5b6fb1ce -> :sswitch_1ab
        0x5bbea4e8 -> :sswitch_19f
        0x5d0fc041 -> :sswitch_193
        0x5d2e5d62 -> :sswitch_187
        0x5e1b8246 -> :sswitch_17b
        0x5f022840 -> :sswitch_16f
        0x62f04df1 -> :sswitch_163
        0x63632339 -> :sswitch_157
        0x636426c6 -> :sswitch_14b
        0x65ba7c9f -> :sswitch_13f
        0x65e59901 -> :sswitch_133
        0x66e405f0 -> :sswitch_127
        0x68fafae7 -> :sswitch_11b
        0x6922d7ce -> :sswitch_10f
        0x6a47f10c -> :sswitch_103
        0x6a49283e -> :sswitch_fa
        0x6c302670 -> :sswitch_ee
        0x6eeaf7af -> :sswitch_e2
        0x70a4c9d9 -> :sswitch_d6
        0x71503e9b -> :sswitch_ca
        0x71fd7398 -> :sswitch_be
        0x751e9427 -> :sswitch_b2
        0x751fff38 -> :sswitch_a6
        0x76acc111 -> :sswitch_9a
        0x78b8a475 -> :sswitch_8e
        0x79e74a9f -> :sswitch_82
        0x79f5ac85 -> :sswitch_76
        0x7a119c4b -> :sswitch_6a
        0x7af58937 -> :sswitch_5e
        0x7b2fc0f6 -> :sswitch_52
        0x7bb64959 -> :sswitch_46
        0x7d9266b8 -> :sswitch_3a
        0x7f3d788c -> :sswitch_2e
    .end sparse-switch
.end method
