package com.microsoft.azure.test.project;

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import com.azure.resourcemanager.resources.models.Subscription;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public final class MyTreeStructureProvider implements TreeStructureProvider {
    private final Project myProject;

    public MyTreeStructureProvider(Project project) {
        myProject = project;
    }

    @Override
    @NotNull
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
        ArrayList<AbstractTreeNode<?>> nodes = new ArrayList<>();
        for (AbstractTreeNode<?> child : children) {
            boolean isDotAzureDir = Optional.ofNullable(child)
                .filter(c -> c instanceof PsiDirectoryNode)
                .map(c -> ((PsiDirectoryNode) c).getVirtualFile())
                .filter(VirtualFile::isDirectory)
                .filter(f -> f.getName().equalsIgnoreCase(".azure"))
                .isPresent();
            if (isDotAzureDir) {
                nodes.add(new DotAzureNode(myProject, ((PsiDirectoryNode) child).getVirtualFile()));
            } else {
                nodes.add(child);
            }
        }
        return nodes;
    }

    static class DotAzureNode extends AbstractTreeNode<VirtualFile> {
        public DotAzureNode(final Project myProject, final VirtualFile file) {
            super(myProject, file);
        }

        @Override
        @NotNull
        public Collection<? extends AbstractTreeNode<?>> getChildren() {
            final VirtualFile dotAzure = this.getValue();
            final ArrayList<AbstractTreeNode<?>> result = new ArrayList<>();
            final Optional<VirtualFile> connectionsXml = Optional.ofNullable(dotAzure.findChild("default")).map(p -> p.findChild("connections.xml"));
            connectionsXml.ifPresent(virtualFile -> result.add(new LocalConnectionsNode(myProject, virtualFile)));
            result.add(new CloudConnectionsNode(myProject, dotAzure));
            return result;
        }

        @Override
        protected void update(@NotNull final PresentationData presentation) {
            presentation.setPresentableText("Azure");
            presentation.setIcon(AllIcons.Providers.Azure);
        }
    }

    static class LocalConnectionsNode extends AbstractTreeNode<VirtualFile> {
        public LocalConnectionsNode(final Project myProject, final VirtualFile file) {
            super(myProject, file);
        }

        @Override
        @NotNull
        public Collection<? extends AbstractTreeNode<?>> getChildren() {
            return Collections.emptyList();
        }

        @Override
        protected void update(@NotNull final PresentationData presentation) {
            presentation.setPresentableText("Local Connections");
            presentation.setIcon(AllIcons.Nodes.HomeFolder);
        }
    }

    static class CloudConnectionsNode extends AbstractTreeNode<VirtualFile> {
        public CloudConnectionsNode(final Project myProject, final VirtualFile file) {
            super(myProject, file);
        }

        @Override
        @NotNull
        public Collection<? extends AbstractTreeNode<?>> getChildren() {
            return Collections.emptyList();
        }

        @Override
        protected void update(@NotNull final PresentationData presentation) {
            presentation.setPresentableText("Cloud Connections");
            presentation.setIcon(AllIcons.Actions.InlayGlobe);
        }
    }

    static class SubscriptionNode extends AbstractTreeNode<Subscription> {
        public SubscriptionNode(final Project myProject, final Subscription subs) {
            super(myProject, subs);
        }

        @Override
        @NotNull
        public Collection<? extends AbstractTreeNode<?>> getChildren() {
            return Collections.emptyList();
        }

        @Override
        protected void update(@NotNull final PresentationData presentation) {
            presentation.setPresentableText(this.getValue().displayName());
            presentation.setIcon(AllIcons.Actions.InlayGlobe);
        }
    }
}


