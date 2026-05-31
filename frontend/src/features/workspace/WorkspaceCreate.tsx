import Button from '@atlaskit/button/new';
import Textfield from '@atlaskit/textfield';
import Form, { Field, ErrorMessage } from '@atlaskit/form';
import { useCreateWorkspaceMutation } from '../../api/contentHubApi';

export function WorkspaceCreate() {
  const [createWorkspace, { isLoading, error }] = useCreateWorkspaceMutation();

  const handleSubmit = async ({ name }: { name: string }) => {
    if (!name.trim()) return;
    await createWorkspace({ name: name.trim() });
  };

  return (
    <div style={{ marginTop: '1.5rem' }}>
      <Form<{ name: string }> onSubmit={handleSubmit}>
        {({ formProps }) => (
          <form {...formProps}>
            <Field name="name" label="New workspace" defaultValue="">
              {({ fieldProps }) => (
                <>
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-start' }}>
                    <div style={{ flex: 1 }}>
                      <Textfield
                        {...fieldProps}
                        placeholder="Workspace name"
                        isDisabled={isLoading}
                      />
                    </div>
                    <div style={{ paddingTop: '1px' }}>
                      <Button type="submit" appearance="primary" isLoading={isLoading}>
                        Create
                      </Button>
                    </div>
                  </div>
                  {error && (
                    <ErrorMessage>Failed to create workspace. Please try again.</ErrorMessage>
                  )}
                </>
              )}
            </Field>
          </form>
        )}
      </Form>
    </div>
  );
}
